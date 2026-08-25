mod audio;
mod decoder;
mod graphics;

use std::collections::VecDeque;
use std::sync::{Arc, mpsc};
use std::thread::{self, JoinHandle};
use std::time::{Duration, Instant};

use ::windows::Win32::Media::MediaFoundation::{MF_VERSION, MFSTARTUP_LITE, MFShutdown, MFStartup};
use ::windows::Win32::Media::{TIMERR_NOERROR, timeBeginPeriod, timeEndPeriod};
use ::windows::Win32::System::Com::{COINIT_MULTITHREADED, CoInitializeEx, CoUninitialize};
use ::windows::Win32::System::Threading::{
    GetCurrentThread, SetThreadPriority, THREAD_PRIORITY_ABOVE_NORMAL,
};

use crate::{
    ADAPTIVE_VIDEO_QUEUE_CAPACITY, BackendConfig, BackendError, BackendEvent, CapabilityProbe,
    Control, LifecycleState, Shared, Subsystem, VideoCodec, WindowsGraphicsApi,
};

use self::audio::AudioRenderer;
use self::decoder::{DecodedVideoFrame, Decoder, take_frame_for_presentation};
use self::graphics::Graphics;

const RECOVERY_TIMEOUT: Duration = Duration::from_secs(5);

/// Keeps the native media loop out of Windows' coarse default timer cadence.
/// The official GFN renderer uses an accurate-sleep pacing path and elevated
/// RTP/media threads; without this, a nominal 1 ms idle sleep can occasionally
/// become a visible multi-frame stall.
struct LowLatencyThreadGuard {
    timer_resolution_active: bool,
}

impl LowLatencyThreadGuard {
    fn enter() -> Self {
        let timer_resolution_active = unsafe { timeBeginPeriod(1) == TIMERR_NOERROR };
        unsafe {
            let _ = SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_ABOVE_NORMAL);
        }
        Self {
            timer_resolution_active,
        }
    }
}

impl Drop for LowLatencyThreadGuard {
    fn drop(&mut self) {
        if self.timer_resolution_active {
            unsafe {
                let _ = timeEndPeriod(1);
            }
        }
    }
}

struct MediaRuntime;

impl MediaRuntime {
    fn initialize() -> Result<Self, BackendError> {
        unsafe {
            CoInitializeEx(None, COINIT_MULTITHREADED)
                .ok()
                .map_err(|error| BackendError::Startup(format!("CoInitializeEx: {error}")))?;
            if let Err(error) = MFStartup(MF_VERSION, MFSTARTUP_LITE) {
                CoUninitialize();
                return Err(BackendError::Startup(format!("MFStartup: {error}")));
            }
        }
        Ok(Self)
    }
}

impl Drop for MediaRuntime {
    fn drop(&mut self) {
        unsafe {
            let _ = MFShutdown();
            CoUninitialize();
        }
    }
}

pub(super) fn probe(api: WindowsGraphicsApi) -> CapabilityProbe {
    let _runtime = match MediaRuntime::initialize() {
        Ok(runtime) => runtime,
        Err(error) => {
            return CapabilityProbe {
                available: false,
                h264_hardware_decode: false,
                h265_hardware_decode: false,
                av1_hardware_decode: false,
                d3d11_presentation: false,
                wasapi_render: false,
                reason: Some(error.to_string()),
            };
        }
    };

    let graphics = Graphics::probe(api);
    let h264_decoder = graphics
        .as_ref()
        .map_err(Clone::clone)
        .and_then(|graphics| {
            Decoder::probe(graphics, VideoCodec::H264).map_err(|error| error.to_string())
        });
    let h265_decoder = graphics
        .as_ref()
        .map_err(Clone::clone)
        .and_then(|graphics| {
            Decoder::probe(graphics, VideoCodec::H265).map_err(|error| error.to_string())
        });
    let av1_decoder = graphics
        .as_ref()
        .map_err(Clone::clone)
        .and_then(|graphics| {
            Decoder::probe(graphics, VideoCodec::Av1).map_err(|error| error.to_string())
        });
    let audio = AudioRenderer::probe().map_err(|error| error.to_string());
    let h264_hardware_decode = h264_decoder.is_ok();
    let h265_hardware_decode = h265_decoder.is_ok();
    let av1_hardware_decode = av1_decoder.is_ok();
    let d3d11_presentation = graphics.is_ok();
    let wasapi_render = audio.is_ok();
    let mut probe = CapabilityProbe {
        available: false,
        h264_hardware_decode,
        h265_hardware_decode,
        av1_hardware_decode,
        d3d11_presentation,
        wasapi_render,
        reason: None,
    };
    probe.available = probe.bundled_backend_available();
    if !probe.available {
        probe.reason = Some(
            [
                graphics.err().map(|error| format!("{api:?}: {error}")),
                h264_decoder.err().map(|error| format!("H.264: {error}")),
                audio.err().map(|error| format!("WASAPI: {error}")),
            ]
            .into_iter()
            .flatten()
            .collect::<Vec<_>>()
            .join("; "),
        );
    }
    probe
}

pub(super) fn spawn(
    api: WindowsGraphicsApi,
    config: BackendConfig,
    shared: Arc<Shared>,
    controls: mpsc::Receiver<Control>,
) -> Result<JoinHandle<()>, BackendError> {
    let (ready_sender, ready_receiver) = mpsc::sync_channel(1);
    let worker_shared = Arc::clone(&shared);
    let worker = thread::Builder::new()
        .name("opennow-windows-media".to_owned())
        .spawn(move || {
            let runtime = match Worker::new(api, config, worker_shared, controls) {
                Ok(worker) => {
                    let _ = ready_sender.send(Ok(()));
                    worker
                }
                Err(error) => {
                    let _ = ready_sender.send(Err(error));
                    return;
                }
            };
            runtime.run();
        })
        .map_err(|error| BackendError::Startup(format!("failed to spawn media thread: {error}")))?;

    match ready_receiver.recv() {
        Ok(Ok(())) => Ok(worker),
        Ok(Err(error)) => {
            let _ = worker.join();
            Err(error)
        }
        Err(_) => {
            let _ = worker.join();
            Err(BackendError::Startup(
                "media thread exited during initialization".to_owned(),
            ))
        }
    }
}

struct Worker {
    api: WindowsGraphicsApi,
    config: BackendConfig,
    shared: Arc<Shared>,
    controls: mpsc::Receiver<Control>,
    graphics: Graphics,
    decoder: Decoder,
    decoded_video: VecDeque<DecodedVideoFrame>,
    first_frame_presented: bool,
    audio: AudioRenderer,
    _runtime: MediaRuntime,
}

impl Worker {
    fn new(
        api: WindowsGraphicsApi,
        config: BackendConfig,
        shared: Arc<Shared>,
        controls: mpsc::Receiver<Control>,
    ) -> Result<Self, BackendError> {
        let runtime = MediaRuntime::initialize()?;
        let graphics = Graphics::new(api, config.surface, config.video)
            .map_err(|error| BackendError::Startup(format!("{api:?} presentation: {error}")))?;
        let decoder = Decoder::new(&graphics, config.video)
            .map_err(|error| BackendError::Startup(format!("Media Foundation H.264: {error}")))?;
        let audio = AudioRenderer::new(config.audio)
            .map_err(|error| BackendError::Startup(format!("WASAPI: {error}")))?;
        shared.set_state(LifecycleState::Running);
        Ok(Self {
            api,
            config,
            shared,
            controls,
            graphics,
            decoder,
            decoded_video: VecDeque::with_capacity(ADAPTIVE_VIDEO_QUEUE_CAPACITY),
            first_frame_presented: false,
            audio,
            _runtime: runtime,
        })
    }

    fn run(mut self) {
        let _low_latency_thread = LowLatencyThreadGuard::enter();
        let mut stopping = false;
        while !stopping {
            let mut did_work = false;
            self.graphics.pump_window_messages();
            while let Ok(control) = self.controls.try_recv() {
                did_work = true;
                match control {
                    Control::Stop => {
                        stopping = true;
                        break;
                    }
                    Control::ReconfigureVideo(format) => {
                        self.config.video = format;
                        self.shared.video.clear();
                        if let Err(error) = self.rebuild_video(
                            Subsystem::VideoDecode,
                            error_label("video reconfiguration"),
                        ) {
                            self.fail(error);
                            return;
                        }
                    }
                    Control::ReconfigureAudio(format) => {
                        self.config.audio = format;
                        self.shared.audio.clear();
                        if let Err(error) = self.rebuild_audio(error_label("audio reconfiguration"))
                        {
                            self.fail(error);
                            return;
                        }
                    }
                    Control::SetSurface(surface) => {
                        self.config.surface = surface;
                        self.decoded_video.clear();
                        self.first_frame_presented = false;
                        if let Err(error) = self.graphics.set_surface(surface, self.config.video) {
                            if let Err(error) = self.recover_video(
                                Subsystem::VideoPresentation,
                                format!("surface reconfiguration failed: {error}"),
                            ) {
                                self.fail(error);
                                return;
                            }
                        } else {
                            self.shared.set_state(LifecycleState::Running);
                        }
                    }
                    Control::SetPaused(paused) => {
                        self.decoded_video.clear();
                        self.first_frame_presented = false;
                        if let Err(error) = self.audio.set_paused(paused) {
                            if let Err(error) = self.rebuild_audio(format!(
                                "audio {} failed: {error}",
                                if paused { "pause" } else { "resume" }
                            )) {
                                self.fail(error);
                                return;
                            }
                        }
                        if !paused {
                            if let Err(error) = self
                                .rebuild_video(Subsystem::VideoDecode, "video resume".to_owned())
                            {
                                self.fail(error);
                                return;
                            }
                        }
                    }
                }
            }
            if stopping {
                break;
            }
            match self.audio.default_endpoint_changed() {
                Ok(true) => {
                    if let Err(error) =
                        self.rebuild_audio("default audio endpoint changed".to_owned())
                    {
                        self.fail(error);
                        return;
                    }
                }
                Ok(false) => {}
                Err(error) => {
                    if let Err(error) =
                        self.rebuild_audio(format!("default audio endpoint check failed: {error}"))
                    {
                        self.fail(error);
                        return;
                    }
                }
            }
            if self
                .shared
                .paused
                .load(std::sync::atomic::Ordering::Acquire)
            {
                thread::sleep(Duration::from_millis(1));
                continue;
            }

            let produced = match self
                .decoder
                .poll_output(&mut self.decoded_video, &self.shared.events)
            {
                Ok(produced) => produced,
                Err(error) => {
                    if let Err(error) = self.recover_video(Subsystem::VideoDecode, error) {
                        self.fail(error);
                        return;
                    }
                    continue;
                }
            };
            did_work |= produced > 0;
            let decoded_format = self.decoder.format();
            if decoded_format != self.config.video {
                if let Err(error) = self.graphics.reconfigure_video(decoded_format) {
                    if let Err(error) = self.recover_video(Subsystem::VideoPresentation, error) {
                        self.fail(error);
                        return;
                    }
                    continue;
                }
                self.config.video = decoded_format;
                *self
                    .shared
                    .video_format
                    .lock()
                    .unwrap_or_else(|error| error.into_inner()) = decoded_format;
            }
            while self.decoder.wants_input() {
                if let Some(frame) = self.shared.video.try_pop() {
                    did_work = true;
                    if let Err(error) = self.decoder.submit(frame) {
                        if let Err(error) = self.recover_video(Subsystem::VideoDecode, error) {
                            self.fail(error);
                            return;
                        }
                        continue;
                    }
                } else {
                    break;
                }
            }
            // DXGI's frame-latency handle behaves like an auto-reset event.
            // Checking it without a frame ready would consume the signal and
            // leave the swap chain waiting forever for a Present that cannot
            // happen. Only consume readiness when presentation can follow.
            if !self.decoded_video.is_empty() {
                match self.graphics.is_present_ready() {
                    Ok(true) => {
                        let (frame, stale_frames) =
                            take_frame_for_presentation(&mut self.decoded_video);
                        for _ in 0..stale_frames {
                            let _ = self
                                .shared
                                .events
                                .push(BackendEvent::QueueOverflow(Subsystem::VideoPresentation));
                        }
                        if let Some(frame) = frame {
                            did_work = true;
                            if let Err(error) = self.graphics.present(
                                &frame.texture,
                                frame.subresource,
                                frame.timestamp_100ns,
                                frame.duration_100ns,
                            ) {
                                if let Err(error) =
                                    self.recover_video(Subsystem::VideoPresentation, error)
                                {
                                    self.fail(error);
                                    return;
                                }
                                continue;
                            }
                            self.shared
                                .presented_frames
                                .fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                            if !self.first_frame_presented && self.graphics.is_visible() {
                                self.first_frame_presented = true;
                                let _ = self.shared.events.push(BackendEvent::FirstFramePresented);
                            }
                        }
                    }
                    Ok(false) => {}
                    Err(error) => {
                        if let Err(error) = self.recover_video(Subsystem::VideoPresentation, error)
                        {
                            self.fail(error);
                            return;
                        }
                        continue;
                    }
                }
            }
            match self.audio.render(&self.shared.audio) {
                Ok(true) => {
                    did_work = true;
                    let _ = self
                        .shared
                        .events
                        .push(BackendEvent::QueueOverflow(Subsystem::Audio));
                }
                Ok(false) => {}
                Err(error) => {
                    if let Err(error) = self.rebuild_audio(error) {
                        self.fail(error);
                        return;
                    }
                }
            }
            if !did_work {
                thread::sleep(Duration::from_millis(1));
            } else {
                thread::yield_now();
            }
        }

        self.decoder.stop();
        self.audio.stop();
    }

    fn recover_video(&mut self, subsystem: Subsystem, message: String) -> Result<(), BackendError> {
        let _ = self.shared.events.push(BackendEvent::DeviceLost {
            subsystem,
            message: message.clone(),
        });
        self.shared.set_state(LifecycleState::Recovering);
        self.shared.video.clear();
        self.decoded_video.clear();
        self.first_frame_presented = false;
        let start = Instant::now();
        loop {
            if self.shared.state() == LifecycleState::Stopping {
                return Ok(());
            }
            self.config.video = *self
                .shared
                .video_format
                .lock()
                .unwrap_or_else(|error| error.into_inner());
            self.config.surface = *self
                .shared
                .surface
                .lock()
                .unwrap_or_else(|error| error.into_inner());
            match Graphics::new(self.api, self.config.surface, self.config.video).and_then(
                |graphics| {
                    Decoder::new(&graphics, self.config.video).map(|decoder| (graphics, decoder))
                },
            ) {
                Ok((graphics, decoder)) => {
                    self.graphics = graphics;
                    self.decoder = decoder;
                    let _ = self
                        .shared
                        .events
                        .push(BackendEvent::DeviceRecovered(subsystem));
                    let _ = self.shared.events.push(BackendEvent::KeyFrameRequired);
                    self.shared.set_state(LifecycleState::Running);
                    return Ok(());
                }
                Err(error) if start.elapsed() < RECOVERY_TIMEOUT => {
                    let _ = error;
                    thread::sleep(Duration::from_millis(100));
                }
                Err(error) => {
                    return Err(BackendError::DeviceLost {
                        subsystem,
                        message: format!("{message}; recovery timed out: {error}"),
                    });
                }
            }
        }
    }

    fn rebuild_video(&mut self, subsystem: Subsystem, message: String) -> Result<(), BackendError> {
        self.decoder.stop();
        self.decoded_video.clear();
        self.first_frame_presented = false;
        match Decoder::new(&self.graphics, self.config.video) {
            Ok(decoder) => {
                self.decoder = decoder;
                self.graphics
                    .reconfigure_video(self.config.video)
                    .map_err(|error| {
                        BackendError::Reconfigure(format!("D3D11 presenter: {error}"))
                    })?;
                let _ = self.shared.events.push(BackendEvent::KeyFrameRequired);
                self.shared.set_state(LifecycleState::Running);
                Ok(())
            }
            Err(error) => self.recover_video(subsystem, format!("{message}: {error}")),
        }
    }

    fn rebuild_audio(&mut self, message: String) -> Result<(), BackendError> {
        let _ = self.shared.events.push(BackendEvent::DeviceLost {
            subsystem: Subsystem::Audio,
            message: message.clone(),
        });
        self.shared.set_state(LifecycleState::Recovering);
        self.shared.audio.clear();
        self.audio.stop();
        let start = Instant::now();
        loop {
            if self.shared.state() == LifecycleState::Stopping {
                return Ok(());
            }
            self.config.audio = *self
                .shared
                .audio_format
                .lock()
                .unwrap_or_else(|error| error.into_inner());
            let paused = self
                .shared
                .paused
                .load(std::sync::atomic::Ordering::Acquire);
            match AudioRenderer::new(self.config.audio).and_then(|mut audio| {
                if paused {
                    audio.set_paused(true)?;
                }
                Ok(audio)
            }) {
                Ok(audio) => {
                    self.audio = audio;
                    let _ = self
                        .shared
                        .events
                        .push(BackendEvent::DeviceRecovered(Subsystem::Audio));
                    self.shared.set_state(LifecycleState::Running);
                    return Ok(());
                }
                Err(error) if start.elapsed() < RECOVERY_TIMEOUT => {
                    let _ = error;
                    thread::sleep(Duration::from_millis(100));
                }
                Err(error) => {
                    return Err(BackendError::DeviceLost {
                        subsystem: Subsystem::Audio,
                        message: format!("{message}; recovery timed out: {error}"),
                    });
                }
            }
        }
    }

    fn fail(&self, error: BackendError) {
        self.shared.set_state(LifecycleState::Failed);
        let _ = self.shared.events.push(BackendEvent::Fatal(error));
        self.shared.video.close();
        self.shared.audio.close();
    }
}

fn error_label(value: &str) -> String {
    value.to_owned()
}
