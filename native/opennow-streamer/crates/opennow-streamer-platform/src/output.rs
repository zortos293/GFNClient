use std::collections::{HashMap, HashSet, VecDeque};
#[cfg(target_os = "windows")]
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

use opennow_streamer_protocol::RenderSurface;
use sdl2::audio::{AudioCallback, AudioDevice, AudioSpecDesired};
use sdl2::pixels::{Color, PixelFormatEnum};
use sdl2::rect::Rect;
use sdl2::render::{Texture, WindowCanvas};

use crate::media::{CapturedInput, CapturedInputQueue, MediaStreamConfig};
use crate::native_surface::NativeSurface;

#[cfg(target_os = "linux")]
use opennow_streamer_platform_linux::DecodedVideoFrame as LinuxDecodedVideoFrame;
#[cfg(target_os = "windows")]
use opennow_streamer_platform_windows::{
    AudioFormat, BackendConfig, BackendEvent, Bounds, OwnedWindow, Subsystem, SurfaceTarget,
    VideoFormat, WindowHandle, WindowsBackend,
};

const AUDIO_SAMPLE_RATE: i32 = 48_000;
const AUDIO_CHANNELS: u8 = 2;
const AUDIO_BUFFER_FRAMES: u16 = 480;
const MAX_AUDIO_LATENCY_MS: usize = 120;

#[derive(Debug)]
pub(crate) struct DecodedVideoFrame {
    pub(crate) width: u32,
    pub(crate) height: u32,
    pub(crate) rgb: Vec<u8>,
}

#[derive(Debug)]
pub(crate) struct OutputBuffers {
    video: Mutex<Option<DecodedVideoFrame>>,
    #[cfg(target_os = "linux")]
    linux_video: Mutex<Option<LinuxDecodedVideoFrame>>,
    audio: Mutex<VecDeque<f32>>,
    audio_capacity: usize,
    captured_input: Arc<CapturedInputQueue>,
}

#[cfg(target_os = "windows")]
pub(crate) struct WindowsBridge {
    backend: Mutex<Option<Arc<WindowsBackend>>>,
    software_fallback: AtomicBool,
    keyframe_required: AtomicBool,
    last_video_mid: Mutex<String>,
}

#[cfg(target_os = "windows")]
impl WindowsBridge {
    pub(crate) fn new() -> Self {
        Self {
            backend: Mutex::new(None),
            software_fallback: AtomicBool::new(false),
            keyframe_required: AtomicBool::new(true),
            last_video_mid: Mutex::new("video".to_owned()),
        }
    }

    pub(crate) fn backend(&self) -> Option<Arc<WindowsBackend>> {
        self.backend
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    fn replace_backend(&self, backend: Option<Arc<WindowsBackend>>) {
        *self
            .backend
            .lock()
            .unwrap_or_else(|error| error.into_inner()) = backend;
    }

    pub(crate) fn use_software(&self) -> bool {
        self.software_fallback.load(Ordering::Acquire)
    }

    pub(crate) fn fall_back_to_software(&self) {
        self.software_fallback.store(true, Ordering::Release);
        self.keyframe_required.store(true, Ordering::Release);
        self.replace_backend(None);
    }

    pub(crate) fn reset(&self) {
        self.software_fallback.store(false, Ordering::Release);
        self.keyframe_required.store(true, Ordering::Release);
        self.replace_backend(None);
    }

    pub(crate) fn set_last_video_mid(&self, mid: &str) {
        *self
            .last_video_mid
            .lock()
            .unwrap_or_else(|error| error.into_inner()) = mid.to_owned();
    }

    pub(crate) fn last_video_mid(&self) -> String {
        self.last_video_mid
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .clone()
    }

    pub(crate) fn keyframe_required(&self) -> bool {
        self.keyframe_required.load(Ordering::Acquire)
    }

    pub(crate) fn require_keyframe(&self) {
        self.keyframe_required.store(true, Ordering::Release);
    }

    pub(crate) fn accept_keyframe(&self) {
        self.keyframe_required.store(false, Ordering::Release);
    }
}

impl OutputBuffers {
    pub(crate) fn new() -> Self {
        Self {
            video: Mutex::new(None),
            #[cfg(target_os = "linux")]
            linux_video: Mutex::new(None),
            audio: Mutex::new(VecDeque::with_capacity(
                AUDIO_SAMPLE_RATE as usize * AUDIO_CHANNELS as usize * MAX_AUDIO_LATENCY_MS / 1_000,
            )),
            audio_capacity: AUDIO_SAMPLE_RATE as usize
                * AUDIO_CHANNELS as usize
                * MAX_AUDIO_LATENCY_MS
                / 1_000,
            captured_input: Arc::new(CapturedInputQueue::default()),
        }
    }

    pub(crate) fn captured_input(&self) -> Arc<CapturedInputQueue> {
        Arc::clone(&self.captured_input)
    }

    pub(crate) fn replace_video(&self, frame: DecodedVideoFrame) -> bool {
        self.video
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .replace(frame)
            .is_some()
    }

    pub(crate) fn take_video(&self) -> Option<DecodedVideoFrame> {
        self.video
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .take()
    }

    #[cfg(target_os = "linux")]
    pub(crate) fn replace_linux_video(&self, frame: LinuxDecodedVideoFrame) -> bool {
        self.linux_video
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .replace(frame)
            .is_some()
    }

    #[cfg(target_os = "linux")]
    fn take_linux_video(&self) -> Option<LinuxDecodedVideoFrame> {
        self.linux_video
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .take()
    }

    pub(crate) fn push_audio(&self, samples: &[f32]) -> usize {
        let mut audio = self.audio.lock().unwrap_or_else(|error| error.into_inner());
        let overflow = audio
            .len()
            .saturating_add(samples.len())
            .saturating_sub(self.audio_capacity);
        if overflow > 0 {
            let drain_count = overflow.min(audio.len());
            audio.drain(..drain_count);
        }
        if samples.len() >= self.audio_capacity {
            audio.clear();
            audio.extend(
                samples[samples.len() - self.audio_capacity..]
                    .iter()
                    .copied(),
            );
        } else {
            audio.extend(samples.iter().copied());
        }
        overflow
    }

    pub(crate) fn clear(&self) {
        self.take_video();
        #[cfg(target_os = "linux")]
        self.take_linux_video();
        self.audio
            .lock()
            .unwrap_or_else(|error| error.into_inner())
            .clear();
    }

    fn fill_audio(&self, destination: &mut [f32]) {
        let mut audio = self.audio.lock().unwrap_or_else(|error| error.into_inner());
        for sample in destination {
            *sample = audio.pop_front().unwrap_or(0.0);
        }
    }
}

#[cfg(target_os = "linux")]
pub(crate) struct LinuxHardwareOutput {
    _sdl: sdl2::Sdl,
    window: sdl2::video::Window,
    event_pump: sdl2::EventPump,
    audio: AudioDevice<StreamAudioCallback>,
    output: Arc<OutputBuffers>,
    native_surface: Result<NativeSurface, String>,
    presenter: Option<opennow_streamer_platform_linux::VulkanPresenter>,
    surface_size: Option<(u32, u32)>,
    visible: bool,
    paused: bool,
}

#[cfg(target_os = "linux")]
impl LinuxHardwareOutput {
    fn initialize(output: Arc<OutputBuffers>) -> Result<Self, String> {
        let sdl = sdl2::init().map_err(|error| format!("SDL initialization failed: {error}"))?;
        let video = sdl
            .video()
            .map_err(|error| format!("SDL video initialization failed: {error}"))?;
        let audio_subsystem = sdl
            .audio()
            .map_err(|error| format!("SDL audio initialization failed: {error}"))?;
        if video.current_video_driver() != "x11" {
            return Err(format!(
                "Linux Vulkan child presentation requires X11/XWayland, but SDL selected {}",
                video.current_video_driver()
            ));
        }
        let window = video
            .window("OpenNOW Stream", 1280, 720)
            .position_centered()
            .resizable()
            .borderless()
            .hidden()
            .vulkan()
            .build()
            .map_err(|error| format!("native Vulkan window creation failed: {error}"))?;
        let native_surface = NativeSurface::new(&window);
        let desired = AudioSpecDesired {
            freq: Some(AUDIO_SAMPLE_RATE),
            channels: Some(AUDIO_CHANNELS),
            samples: Some(AUDIO_BUFFER_FRAMES),
        };
        let callback_output = Arc::clone(&output);
        let audio = audio_subsystem
            .open_playback(None, &desired, move |_| StreamAudioCallback {
                output: callback_output,
            })
            .map_err(|error| format!("native audio output creation failed: {error}"))?;
        let event_pump = sdl
            .event_pump()
            .map_err(|error| format!("native window event pump creation failed: {error}"))?;
        Ok(Self {
            _sdl: sdl,
            window,
            event_pump,
            audio,
            output,
            native_surface,
            presenter: None,
            surface_size: None,
            visible: false,
            paused: false,
        })
    }

    fn start(&mut self, surface: Option<&RenderSurface>) -> Result<(), String> {
        self.paused = false;
        self.output.clear();
        self.audio.resume();
        if let Some(surface) = surface {
            self.update_surface(surface)?;
        }
        Ok(())
    }

    fn set_paused(&mut self, paused: bool) {
        self.paused = paused;
        if paused {
            self.audio.pause();
            self.output.clear();
        } else {
            self.audio.resume();
        }
    }

    fn stop(&mut self) {
        self.output.clear();
        self.audio.pause();
        self.presenter = None;
        self.surface_size = None;
        if let Ok(surface) = self.native_surface.as_mut() {
            surface.hide();
        }
        self.visible = false;
        self.paused = false;
    }

    fn update_surface(&mut self, surface: &RenderSurface) -> Result<(), String> {
        let Some(rect) = surface.rect.filter(|_| surface.visible) else {
            if let Ok(native_surface) = self.native_surface.as_mut() {
                native_surface.hide();
            }
            self.presenter = None;
            self.surface_size = None;
            self.visible = false;
            return Ok(());
        };
        let parent_handle = surface
            .window_handle
            .as_deref()
            .ok_or_else(|| "visible native surface is missing Electron windowHandle".to_owned())?;
        self.native_surface
            .as_mut()
            .map_err(|error| error.clone())?
            .attach_and_show(
                parent_handle,
                rect,
                surface.screen_rect,
                surface.device_scale_factor,
            )?;
        let size = (rect.width.max(2), rect.height.max(2));
        if self.presenter.is_none() {
            self.presenter = Some(create_linux_presenter(&self.window, size.0, size.1)?);
            self.surface_size = Some(size);
        } else if self.surface_size != Some(size) {
            if let Some(presenter) = self.presenter.as_mut() {
                presenter
                    .reconfigure(size.0, size.1)
                    .map_err(|error| error.to_string())?;
            }
        }
        self.surface_size = Some(size);
        self.visible = true;
        Ok(())
    }

    fn pump(&mut self) -> Result<bool, String> {
        if let Ok(surface) = self.native_surface.as_mut() {
            surface.refresh_ordering()?;
        }
        for event in self.event_pump.poll_iter() {
            if matches!(event, sdl2::event::Event::Quit { .. }) {
                if let Ok(surface) = self.native_surface.as_mut() {
                    surface.hide();
                }
                self.presenter = None;
                self.visible = false;
            }
        }
        if self.paused || !self.visible {
            self.output.take_linux_video();
            return Ok(false);
        }
        let Some(frame) = self.output.take_linux_video() else {
            return Ok(false);
        };
        self.presenter
            .as_mut()
            .ok_or_else(|| {
                "Linux Vulkan presenter is not attached to a visible surface".to_owned()
            })?
            .present(&frame)
            .map_err(|error| error.to_string())?;
        Ok(true)
    }
}

#[cfg(target_os = "linux")]
fn create_linux_presenter(
    window: &sdl2::video::Window,
    width: u32,
    height: u32,
) -> Result<opennow_streamer_platform_linux::VulkanPresenter, String> {
    use std::ffi::c_void;
    use std::num::NonZeroU64;
    use std::ptr::NonNull;

    use raw_window_handle::{HasDisplayHandle, HasWindowHandle, RawDisplayHandle, RawWindowHandle};

    let (display, screen) = match window
        .display_handle()
        .map_err(|error| format!("SDL X11 display handle unavailable: {error}"))?
        .as_raw()
    {
        RawDisplayHandle::Xlib(handle) => (
            handle
                .display
                .ok_or_else(|| "SDL X11 display pointer is null".to_owned())?,
            handle.screen,
        ),
        _ => return Err("SDL did not create an X11 display for Vulkan presentation".to_owned()),
    };
    let window_id = match window
        .window_handle()
        .map_err(|error| format!("SDL X11 window handle unavailable: {error}"))?
        .as_raw()
    {
        RawWindowHandle::Xlib(handle) => NonZeroU64::new(handle.window)
            .ok_or_else(|| "SDL returned an empty X11 window handle".to_owned())?,
        _ => return Err("SDL did not create an X11 window for Vulkan presentation".to_owned()),
    };
    let display = NonNull::<c_void>::new(display.as_ptr().cast())
        .ok_or_else(|| "SDL X11 display pointer is null".to_owned())?;
    let surface = unsafe {
        opennow_streamer_platform_linux::NativeSurface::borrow_x11(display, window_id, screen)
    };
    opennow_streamer_platform_linux::VulkanPresenter::new(&surface, width, height)
        .map_err(|error| error.to_string())
}

struct StreamAudioCallback {
    output: Arc<OutputBuffers>,
}

impl AudioCallback for StreamAudioCallback {
    type Channel = f32;

    fn callback(&mut self, output: &mut [f32]) {
        self.output.fill_audio(output);
    }
}

pub(crate) struct SoftwareOutput {
    _sdl: sdl2::Sdl,
    canvas: WindowCanvas,
    texture: Option<Texture>,
    texture_size: Option<(u32, u32)>,
    event_pump: sdl2::EventPump,
    audio: AudioDevice<StreamAudioCallback>,
    output: Arc<OutputBuffers>,
    native_surface: Result<NativeSurface, String>,
    captured_input: Vec<CapturedInput>,
    pressed_keys: HashMap<sdl2::keyboard::Scancode, u16>,
    pressed_buttons: HashSet<u8>,
    capture_input: bool,
    relative_mouse: bool,
    visible: bool,
    paused: bool,
}

impl SoftwareOutput {
    fn initialize(output: Arc<OutputBuffers>) -> Result<Self, String> {
        let sdl = sdl2::init().map_err(|error| format!("SDL initialization failed: {error}"))?;
        let video = sdl
            .video()
            .map_err(|error| format!("SDL video initialization failed: {error}"))?;
        let audio_subsystem = sdl
            .audio()
            .map_err(|error| format!("SDL audio initialization failed: {error}"))?;
        let window = video
            .window("OpenNOW Stream", 1280, 720)
            .position_centered()
            .resizable()
            .borderless()
            .hidden()
            .metal_view()
            .build()
            .map_err(|error| format!("native video window creation failed: {error}"))?;
        let mut canvas = window
            .into_canvas()
            .build()
            .map_err(|error| format!("native video renderer creation failed: {error}"))?;
        canvas.set_draw_color(Color::BLACK);
        canvas.clear();
        canvas.present();

        let desired = AudioSpecDesired {
            freq: Some(AUDIO_SAMPLE_RATE),
            channels: Some(AUDIO_CHANNELS),
            samples: Some(AUDIO_BUFFER_FRAMES),
        };
        let callback_output = Arc::clone(&output);
        let audio = audio_subsystem
            .open_playback(None, &desired, move |_| StreamAudioCallback {
                output: callback_output,
            })
            .map_err(|error| format!("native audio output creation failed: {error}"))?;
        if audio.spec().freq != AUDIO_SAMPLE_RATE || audio.spec().channels != AUDIO_CHANNELS {
            return Err(format!(
                "native audio output returned unsupported format: {} Hz, {} channels",
                audio.spec().freq,
                audio.spec().channels
            ));
        }
        let event_pump = sdl
            .event_pump()
            .map_err(|error| format!("native window event pump creation failed: {error}"))?;
        let native_surface = if video.current_video_driver() == "dummy" {
            Err("dummy SDL video driver has no native presentation handle".to_owned())
        } else {
            NativeSurface::new(canvas.window())
        };

        Ok(Self {
            _sdl: sdl,
            canvas,
            texture: None,
            texture_size: None,
            event_pump,
            audio,
            output,
            native_surface,
            captured_input: Vec::new(),
            pressed_keys: HashMap::new(),
            pressed_buttons: HashSet::new(),
            capture_input: cfg!(target_os = "windows"),
            relative_mouse: false,
            visible: false,
            paused: false,
        })
    }

    fn start(&mut self, surface: Option<&RenderSurface>) -> Result<(), String> {
        self.paused = false;
        self.output.clear();
        self.audio.resume();
        if let Some(surface) = surface {
            self.update_surface(surface)?;
        }
        Ok(())
    }

    fn set_paused(&mut self, paused: bool) {
        self.paused = paused;
        if paused {
            self.release_captured_input();
            self.audio.pause();
            self.output.clear();
        } else {
            self.audio.resume();
        }
    }

    fn stop(&mut self) {
        self.release_captured_input();
        self.flush_captured_input();
        self.audio.pause();
        self.output.clear();
        if let Ok(surface) = self.native_surface.as_mut() {
            surface.hide();
        }
        self.visible = false;
        self.paused = false;
        self.texture = None;
        self.texture_size = None;
    }

    fn update_surface(&mut self, surface: &RenderSurface) -> Result<(), String> {
        let Some(rect) = surface.rect.filter(|_| surface.visible) else {
            if let Ok(native_surface) = self.native_surface.as_mut() {
                native_surface.hide();
            }
            self.visible = false;
            return Ok(());
        };
        let parent_handle = surface
            .window_handle
            .as_deref()
            .ok_or_else(|| "visible native surface is missing Electron windowHandle".to_owned())?;
        self.native_surface
            .as_mut()
            .map_err(|error| error.clone())?
            .attach_and_show(
                parent_handle,
                rect,
                surface.screen_rect,
                surface.device_scale_factor,
            )?;
        self.visible = true;
        Ok(())
    }

    fn pump(&mut self) -> Result<bool, String> {
        if let Ok(surface) = self.native_surface.as_mut() {
            surface.refresh_ordering()?;
        }
        let events = self.event_pump.poll_iter().collect::<Vec<_>>();
        for event in events {
            if matches!(event, sdl2::event::Event::Quit { .. }) {
                self.release_captured_input();
                if let Ok(surface) = self.native_surface.as_mut() {
                    surface.hide();
                }
                self.visible = false;
            } else if self.capture_input {
                self.capture_input_event(event);
            }
        }
        if self.paused || !self.visible {
            self.output.take_video();
            return Ok(false);
        }
        let Some(frame) = self.output.take_video() else {
            return Ok(false);
        };
        if self.texture_size != Some((frame.width, frame.height)) {
            self.texture = Some(
                self.canvas
                    .texture_creator()
                    .create_texture_streaming(PixelFormatEnum::RGB24, frame.width, frame.height)
                    .map_err(|error| format!("video texture creation failed: {error}"))?,
            );
            self.texture_size = Some((frame.width, frame.height));
        }
        let texture = self.texture.as_mut().expect("texture was just created");
        texture
            .update(None, &frame.rgb, frame.width as usize * 3)
            .map_err(|error| format!("video texture upload failed: {error}"))?;
        let (output_width, output_height) = self.canvas.output_size()?;
        let target = aspect_fit(frame.width, frame.height, output_width, output_height);
        self.canvas.set_draw_color(Color::BLACK);
        self.canvas.clear();
        self.canvas.copy(texture, None, target)?;
        self.canvas.present();
        Ok(true)
    }

    fn take_captured_input(&mut self) -> Vec<CapturedInput> {
        std::mem::take(&mut self.captured_input)
    }

    fn flush_captured_input(&mut self) {
        let captured_input = self.output.captured_input();
        for input in self.take_captured_input() {
            captured_input.push(input);
        }
    }

    fn capture_input_event(&mut self, event: sdl2::event::Event) {
        use sdl2::event::{Event, WindowEvent};

        match event {
            Event::KeyDown {
                scancode: Some(scancode),
                keymod,
                repeat: false,
                ..
            } => {
                let Some(virtual_key) = sdl_virtual_key(scancode) else {
                    return;
                };
                if self.pressed_keys.insert(scancode, virtual_key).is_none() {
                    self.captured_input.push(CapturedInput::Key {
                        virtual_key,
                        modifiers: sdl_modifiers(scancode, keymod),
                        pressed: true,
                    });
                }
            }
            Event::KeyUp {
                scancode: Some(scancode),
                keymod,
                ..
            } => {
                if let Some(virtual_key) = self.pressed_keys.remove(&scancode) {
                    self.captured_input.push(CapturedInput::Key {
                        virtual_key,
                        modifiers: sdl_modifiers(scancode, keymod),
                        pressed: false,
                    });
                }
            }
            Event::MouseMotion { xrel, yrel, .. } if self.relative_mouse => {
                push_mouse_motion(&mut self.captured_input, xrel, yrel);
            }
            Event::MouseButtonDown { mouse_btn, .. } => {
                self.enable_relative_mouse();
                if let Some(button) = sdl_mouse_button(mouse_btn)
                    && self.pressed_buttons.insert(button)
                {
                    self.captured_input.push(CapturedInput::MouseButton {
                        button,
                        pressed: true,
                    });
                }
            }
            Event::MouseButtonUp { mouse_btn, .. } => {
                if let Some(button) = sdl_mouse_button(mouse_btn)
                    && self.pressed_buttons.remove(&button)
                {
                    self.captured_input.push(CapturedInput::MouseButton {
                        button,
                        pressed: false,
                    });
                }
            }
            Event::MouseWheel { y, direction, .. } if self.relative_mouse && y != 0 => {
                let direction = if direction == sdl2::mouse::MouseWheelDirection::Flipped {
                    -1
                } else {
                    1
                };
                self.captured_input.push(CapturedInput::MouseWheel {
                    delta: clamp_i16(y.saturating_mul(120).saturating_mul(direction)),
                });
            }
            Event::Window {
                win_event: WindowEvent::FocusLost,
                ..
            } => self.release_captured_input(),
            _ => {}
        }
    }

    fn enable_relative_mouse(&mut self) {
        if !self.relative_mouse {
            self._sdl.mouse().set_relative_mouse_mode(true);
            self.relative_mouse = true;
        }
    }

    fn release_captured_input(&mut self) {
        for (_, virtual_key) in self.pressed_keys.drain() {
            self.captured_input.push(CapturedInput::Key {
                virtual_key,
                modifiers: 0,
                pressed: false,
            });
        }
        for button in self.pressed_buttons.drain() {
            self.captured_input.push(CapturedInput::MouseButton {
                button,
                pressed: false,
            });
        }
        if self.relative_mouse {
            self._sdl.mouse().set_relative_mouse_mode(false);
            self.relative_mouse = false;
        }
    }
}

fn sdl_virtual_key(scancode: sdl2::keyboard::Scancode) -> Option<u16> {
    use sdl2::keyboard::Scancode;

    Some(match scancode {
        Scancode::A => 0x41,
        Scancode::B => 0x42,
        Scancode::C => 0x43,
        Scancode::D => 0x44,
        Scancode::E => 0x45,
        Scancode::F => 0x46,
        Scancode::G => 0x47,
        Scancode::H => 0x48,
        Scancode::I => 0x49,
        Scancode::J => 0x4a,
        Scancode::K => 0x4b,
        Scancode::L => 0x4c,
        Scancode::M => 0x4d,
        Scancode::N => 0x4e,
        Scancode::O => 0x4f,
        Scancode::P => 0x50,
        Scancode::Q => 0x51,
        Scancode::R => 0x52,
        Scancode::S => 0x53,
        Scancode::T => 0x54,
        Scancode::U => 0x55,
        Scancode::V => 0x56,
        Scancode::W => 0x57,
        Scancode::X => 0x58,
        Scancode::Y => 0x59,
        Scancode::Z => 0x5a,
        Scancode::Num0 => 0x30,
        Scancode::Num1 => 0x31,
        Scancode::Num2 => 0x32,
        Scancode::Num3 => 0x33,
        Scancode::Num4 => 0x34,
        Scancode::Num5 => 0x35,
        Scancode::Num6 => 0x36,
        Scancode::Num7 => 0x37,
        Scancode::Num8 => 0x38,
        Scancode::Num9 => 0x39,
        Scancode::Return | Scancode::KpEnter => 0x0d,
        Scancode::Escape => 0x1b,
        Scancode::Backspace => 0x08,
        Scancode::Tab => 0x09,
        Scancode::Space => 0x20,
        Scancode::Minus => 0xbd,
        Scancode::Equals | Scancode::KpEquals => 0xbb,
        Scancode::LeftBracket => 0xdb,
        Scancode::RightBracket => 0xdd,
        Scancode::Backslash => 0xdc,
        Scancode::NonUsBackslash => 0xe2,
        Scancode::Semicolon => 0xba,
        Scancode::Apostrophe => 0xde,
        Scancode::Grave => 0xc0,
        Scancode::Comma => 0xbc,
        Scancode::Period => 0xbe,
        Scancode::Slash => 0xbf,
        Scancode::F1 => 0x70,
        Scancode::F2 => 0x71,
        Scancode::F3 => 0x72,
        Scancode::F4 => 0x73,
        Scancode::F5 => 0x74,
        Scancode::F6 => 0x75,
        Scancode::F7 => 0x76,
        Scancode::F8 => 0x77,
        Scancode::F9 => 0x78,
        Scancode::F10 => 0x79,
        Scancode::F11 => 0x7a,
        Scancode::F12 => 0x7b,
        Scancode::F13 => 0x7c,
        Scancode::F14 => 0x7d,
        Scancode::F15 => 0x7e,
        Scancode::F16 => 0x7f,
        Scancode::F17 => 0x80,
        Scancode::F18 => 0x81,
        Scancode::F19 => 0x82,
        Scancode::F20 => 0x83,
        Scancode::F21 => 0x84,
        Scancode::F22 => 0x85,
        Scancode::F23 => 0x86,
        Scancode::F24 => 0x87,
        Scancode::Right => 0x27,
        Scancode::Left => 0x25,
        Scancode::Down => 0x28,
        Scancode::Up => 0x26,
        Scancode::LCtrl => 0xa2,
        Scancode::LShift => 0xa0,
        Scancode::LAlt => 0xa4,
        Scancode::LGui => 0x5b,
        Scancode::RCtrl => 0xa3,
        Scancode::RShift => 0xa1,
        Scancode::RAlt => 0xa5,
        Scancode::RGui => 0x5c,
        Scancode::CapsLock => 0x14,
        Scancode::NumLockClear => 0x90,
        Scancode::Insert => 0x2d,
        Scancode::Delete => 0x2e,
        Scancode::Home => 0x24,
        Scancode::End => 0x23,
        Scancode::PageUp => 0x21,
        Scancode::PageDown => 0x22,
        Scancode::PrintScreen => 0x2a,
        Scancode::ScrollLock => 0x91,
        Scancode::Pause => 0x13,
        Scancode::Application => 0x5d,
        Scancode::Kp0 => 0x60,
        Scancode::Kp1 => 0x61,
        Scancode::Kp2 => 0x62,
        Scancode::Kp3 => 0x63,
        Scancode::Kp4 => 0x64,
        Scancode::Kp5 => 0x65,
        Scancode::Kp6 => 0x66,
        Scancode::Kp7 => 0x67,
        Scancode::Kp8 => 0x68,
        Scancode::Kp9 => 0x69,
        Scancode::KpPlus => 0x6b,
        Scancode::KpMinus => 0x6d,
        Scancode::KpMultiply => 0x6a,
        Scancode::KpDivide => 0x6f,
        Scancode::KpPeriod => 0x6e,
        _ => return None,
    })
}

fn sdl_modifiers(scancode: sdl2::keyboard::Scancode, modifiers: sdl2::keyboard::Mod) -> u16 {
    use sdl2::keyboard::{Mod, Scancode};

    let mut flags = 0;
    if !matches!(scancode, Scancode::LShift | Scancode::RShift)
        && modifiers.intersects(Mod::LSHIFTMOD | Mod::RSHIFTMOD)
    {
        flags |= 0x01;
    }
    if !matches!(scancode, Scancode::LCtrl | Scancode::RCtrl)
        && modifiers.intersects(Mod::LCTRLMOD | Mod::RCTRLMOD)
    {
        flags |= 0x02;
    }
    if !matches!(scancode, Scancode::LAlt | Scancode::RAlt)
        && modifiers.intersects(Mod::LALTMOD | Mod::RALTMOD)
    {
        flags |= 0x04;
    }
    if !matches!(scancode, Scancode::LGui | Scancode::RGui)
        && modifiers.intersects(Mod::LGUIMOD | Mod::RGUIMOD)
    {
        flags |= 0x08;
    }
    flags
}

fn sdl_mouse_button(button: sdl2::mouse::MouseButton) -> Option<u8> {
    use sdl2::mouse::MouseButton;

    match button {
        MouseButton::Left => Some(1),
        MouseButton::Middle => Some(2),
        MouseButton::Right => Some(3),
        MouseButton::X1 => Some(4),
        MouseButton::X2 => Some(5),
        MouseButton::Unknown => None,
    }
}

fn push_mouse_motion(input: &mut Vec<CapturedInput>, delta_x: i32, delta_y: i32) {
    if delta_x == 0 && delta_y == 0 {
        return;
    }
    if let Some(CapturedInput::MouseMove {
        delta_x: pending_x,
        delta_y: pending_y,
    }) = input.last_mut()
    {
        *pending_x = clamp_i16(i32::from(*pending_x).saturating_add(delta_x));
        *pending_y = clamp_i16(i32::from(*pending_y).saturating_add(delta_y));
        return;
    }
    input.push(CapturedInput::MouseMove {
        delta_x: clamp_i16(delta_x),
        delta_y: clamp_i16(delta_y),
    });
}

fn clamp_i16(value: i32) -> i16 {
    value.clamp(i32::from(i16::MIN), i32::from(i16::MAX)) as i16
}

pub(crate) enum ActiveOutput {
    Software(Box<SoftwareOutput>),
    #[cfg(target_os = "windows")]
    Windows(WindowsOutput),
    #[cfg(target_os = "linux")]
    LinuxHardware(Box<LinuxHardwareOutput>),
    #[cfg(target_os = "macos")]
    Mac(crate::macos_backend::MacOutput),
}

impl ActiveOutput {
    #[cfg(target_os = "macos")]
    pub(crate) fn is_macos_hardware(&self) -> bool {
        matches!(self, Self::Mac(_))
    }

    #[cfg(target_os = "linux")]
    pub(crate) fn is_linux_hardware(&self) -> bool {
        matches!(self, Self::LinuxHardware(_))
    }

    pub(crate) fn initialize(
        output: Arc<OutputBuffers>,
        use_hardware: bool,
        stream: MediaStreamConfig,
        #[cfg(target_os = "windows")] windows_bridge: Arc<WindowsBridge>,
        use_linux_hardware: bool,
    ) -> Result<Self, String> {
        #[cfg(target_os = "macos")]
        if use_hardware {
            return Ok(Self::Mac(crate::macos_backend::MacOutput::initialize()));
        }
        #[cfg(target_os = "windows")]
        if use_hardware {
            return WindowsOutput::initialize(windows_bridge, stream).map(Self::Windows);
        }
        #[cfg(target_os = "linux")]
        if use_linux_hardware {
            return LinuxHardwareOutput::initialize(output)
                .map(Box::new)
                .map(Self::LinuxHardware);
        }
        let _ = (use_hardware, stream);
        let _ = use_linux_hardware;
        SoftwareOutput::initialize(output)
            .map(Box::new)
            .map(Self::Software)
    }

    pub(crate) fn start(&mut self, surface: Option<&RenderSurface>) -> Result<(), String> {
        match self {
            Self::Software(output) => output.start(surface),
            #[cfg(target_os = "windows")]
            Self::Windows(output) => output.start(surface),
            #[cfg(target_os = "linux")]
            Self::LinuxHardware(output) => output.start(surface),
            #[cfg(target_os = "macos")]
            Self::Mac(output) => output.start(surface),
        }
    }

    pub(crate) fn set_paused(&mut self, paused: bool) -> Result<(), String> {
        match self {
            Self::Software(output) => {
                output.set_paused(paused);
                Ok(())
            }
            #[cfg(target_os = "windows")]
            Self::Windows(output) => output.set_paused(paused),
            #[cfg(target_os = "linux")]
            Self::LinuxHardware(output) => {
                output.set_paused(paused);
                Ok(())
            }
            #[cfg(target_os = "macos")]
            Self::Mac(output) => output.set_paused(paused),
        }
    }

    pub(crate) fn stop(&mut self) {
        match self {
            Self::Software(output) => output.stop(),
            #[cfg(target_os = "windows")]
            Self::Windows(output) => output.stop(),
            #[cfg(target_os = "linux")]
            Self::LinuxHardware(output) => output.stop(),
            #[cfg(target_os = "macos")]
            Self::Mac(output) => output.stop(),
        }
    }

    pub(crate) fn update_surface(&mut self, surface: &RenderSurface) -> Result<(), String> {
        match self {
            Self::Software(output) => output.update_surface(surface),
            #[cfg(target_os = "windows")]
            Self::Windows(output) => output.update_surface(surface),
            #[cfg(target_os = "linux")]
            Self::LinuxHardware(output) => output.update_surface(surface),
            #[cfg(target_os = "macos")]
            Self::Mac(output) => output.update_surface(surface),
        }
    }

    pub(crate) fn pump(&mut self) -> Result<OutputEvent, String> {
        match self {
            Self::Software(output) => output.pump().map(|presented| {
                if presented {
                    OutputEvent::Presented("OpenH264/SDL")
                } else {
                    OutputEvent::None
                }
            }),
            #[cfg(target_os = "windows")]
            Self::Windows(output) => output.pump(),
            #[cfg(target_os = "linux")]
            Self::LinuxHardware(output) => output.pump().map(|presented| {
                if presented {
                    OutputEvent::Presented("VA-API/V4L2/Vulkan")
                } else {
                    OutputEvent::None
                }
            }),
            #[cfg(target_os = "macos")]
            Self::Mac(output) => output.pump().map(|_| OutputEvent::None),
        }
    }

    pub(crate) fn take_captured_input(&mut self) -> Vec<CapturedInput> {
        match self {
            Self::Software(output) => output.take_captured_input(),
            #[cfg(target_os = "windows")]
            Self::Windows(_) => Vec::new(),
            #[cfg(target_os = "linux")]
            Self::LinuxHardware(_) => Vec::new(),
            #[cfg(target_os = "macos")]
            Self::Mac(_) => Vec::new(),
        }
    }

    #[cfg(target_os = "macos")]
    pub(crate) fn configure_macos_h264(
        &mut self,
        parameter_sets: opennow_streamer_platform_macos::H264ParameterSets,
    ) -> Result<opennow_streamer_platform_macos::StreamSink, String> {
        match self {
            Self::Mac(output) => output.configure_h264(parameter_sets),
            Self::Software(_) => Err("VideoToolbox is not the selected media backend".to_owned()),
        }
    }
}

pub(crate) enum OutputEvent {
    None,
    Presented(&'static str),
    #[cfg(target_os = "windows")]
    RequestKeyframe,
    #[cfg(target_os = "windows")]
    DeviceLost {
        subsystem: &'static str,
        recovered: bool,
        message: Option<String>,
    },
    #[cfg(target_os = "windows")]
    QueueDropped(&'static str),
    #[cfg(target_os = "windows")]
    Fatal(String),
}

#[cfg(target_os = "windows")]
pub(crate) struct WindowsOutput {
    backend: Arc<WindowsBackend>,
    bridge: Arc<WindowsBridge>,
    stopped: bool,
}

#[cfg(target_os = "windows")]
impl WindowsOutput {
    fn initialize(bridge: Arc<WindowsBridge>, stream: MediaStreamConfig) -> Result<Self, String> {
        bridge.reset();
        let backend = Arc::new(
            WindowsBackend::start(BackendConfig {
                video: VideoFormat {
                    width: stream.width,
                    height: stream.height,
                    frame_rate_numerator: std::num::NonZeroU32::new(stream.fps.max(1))
                        .expect("fps is clamped non-zero"),
                    frame_rate_denominator: std::num::NonZeroU32::new(1).expect("one is non-zero"),
                    average_bitrate: stream.bitrate_bps.max(1),
                },
                audio: AudioFormat {
                    sample_rate: AUDIO_SAMPLE_RATE as u32,
                    channels: AUDIO_CHANNELS as u16,
                },
                surface: hidden_windows_surface(),
                video_queue_capacity: 3,
                audio_queue_capacity: 12,
            })
            .map_err(|error| error.to_string())?,
        );
        bridge.replace_backend(Some(Arc::clone(&backend)));
        Ok(Self {
            backend,
            bridge,
            stopped: false,
        })
    }

    fn start(&mut self, surface: Option<&RenderSurface>) -> Result<(), String> {
        if let Some(surface) = surface {
            self.update_surface(surface)?;
        }
        Ok(())
    }

    fn set_paused(&self, paused: bool) -> Result<(), String> {
        self.backend
            .set_paused(paused)
            .map_err(|error| error.to_string())
    }

    fn update_surface(&self, surface: &RenderSurface) -> Result<(), String> {
        self.backend
            .set_surface(windows_surface(surface)?)
            .map_err(|error| error.to_string())
    }

    fn pump(&self) -> Result<OutputEvent, String> {
        let Some(event) = self.backend.try_event() else {
            return Ok(OutputEvent::None);
        };
        Ok(match event {
            BackendEvent::FirstFramePresented => {
                OutputEvent::Presented("Media Foundation/D3D11/WASAPI")
            }
            BackendEvent::KeyFrameRequired => {
                self.bridge.require_keyframe();
                OutputEvent::RequestKeyframe
            }
            BackendEvent::DeviceLost { subsystem, message } => OutputEvent::DeviceLost {
                subsystem: subsystem_label(subsystem),
                recovered: false,
                message: Some(message),
            },
            BackendEvent::DeviceRecovered(subsystem) => OutputEvent::DeviceLost {
                subsystem: subsystem_label(subsystem),
                recovered: true,
                message: None,
            },
            BackendEvent::Fatal(error) => OutputEvent::Fatal(error.to_string()),
            BackendEvent::QueueOverflow(subsystem) => {
                if subsystem == Subsystem::VideoDecode {
                    self.bridge.require_keyframe();
                }
                OutputEvent::QueueDropped(subsystem_label(subsystem))
            }
            BackendEvent::StateChanged(_) | BackendEvent::VideoFormatChanged(_) => {
                OutputEvent::None
            }
        })
    }

    fn stop(&mut self) {
        if self.stopped {
            return;
        }
        self.bridge.replace_backend(None);
        self.backend.stop();
        self.stopped = true;
    }
}

#[cfg(target_os = "windows")]
impl Drop for WindowsOutput {
    fn drop(&mut self) {
        self.stop();
    }
}

#[cfg(target_os = "windows")]
fn hidden_windows_surface() -> SurfaceTarget {
    SurfaceTarget::Owned(OwnedWindow {
        parent: None,
        bounds: Bounds {
            x: 0,
            y: 0,
            width: 2,
            height: 2,
        },
        visible: false,
    })
}

#[cfg(target_os = "windows")]
fn windows_surface(surface: &RenderSurface) -> Result<SurfaceTarget, String> {
    let Some(rect) = surface.rect.filter(|_| surface.visible) else {
        return Ok(hidden_windows_surface());
    };
    let parent = surface
        .window_handle
        .as_deref()
        .ok_or_else(|| "visible native surface is missing Electron windowHandle".to_owned())?;
    let parent = parse_windows_handle(parent)?;
    Ok(SurfaceTarget::Owned(OwnedWindow {
        parent: Some(parent),
        bounds: Bounds {
            x: rect.x,
            y: rect.y,
            width: rect.width.max(2),
            height: rect.height.max(2),
        },
        visible: true,
    }))
}

#[cfg(target_os = "windows")]
fn parse_windows_handle(value: &str) -> Result<WindowHandle, String> {
    let trimmed = value.trim();
    let raw = if let Some(hex) = trimmed
        .strip_prefix("0x")
        .or_else(|| trimmed.strip_prefix("0X"))
    {
        isize::from_str_radix(hex, 16)
    } else {
        trimmed.parse::<isize>()
    }
    .map_err(|error| format!("invalid Electron HWND {value:?}: {error}"))?;
    WindowHandle::new(raw).ok_or_else(|| "Electron HWND must be non-zero".to_owned())
}

#[cfg(target_os = "windows")]
fn subsystem_label(subsystem: Subsystem) -> &'static str {
    match subsystem {
        Subsystem::VideoDecode => "video-decode",
        Subsystem::VideoPresentation => "video-presentation",
        Subsystem::Audio => "audio",
    }
}

fn aspect_fit(source_width: u32, source_height: u32, width: u32, height: u32) -> Rect {
    let scale = (width as f64 / source_width as f64).min(height as f64 / source_height as f64);
    let target_width = (source_width as f64 * scale).round() as u32;
    let target_height = (source_height as f64 * scale).round() as u32;
    Rect::new(
        ((width - target_width) / 2) as i32,
        ((height - target_height) / 2) as i32,
        target_width,
        target_height,
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn audio_buffer_drops_oldest_samples() {
        let output = OutputBuffers {
            video: Mutex::new(None),
            #[cfg(target_os = "linux")]
            linux_video: Mutex::new(None),
            audio: Mutex::new(VecDeque::new()),
            audio_capacity: 4,
            captured_input: Arc::new(CapturedInputQueue::default()),
        };
        assert_eq!(output.push_audio(&[1.0, 2.0, 3.0]), 0);
        assert_eq!(output.push_audio(&[4.0, 5.0, 6.0]), 2);
        let mut values = [0.0; 4];
        output.fill_audio(&mut values);
        assert_eq!(values, [3.0, 4.0, 5.0, 6.0]);
    }

    #[test]
    fn video_slot_keeps_only_the_latest_frame() {
        let output = OutputBuffers::new();
        assert!(!output.replace_video(DecodedVideoFrame {
            width: 1,
            height: 1,
            rgb: vec![1, 2, 3],
        }));
        assert!(output.replace_video(DecodedVideoFrame {
            width: 2,
            height: 1,
            rgb: vec![4; 6],
        }));
        assert_eq!(output.take_video().expect("frame").width, 2);
    }

    #[test]
    fn aspect_fit_letterboxes_without_stretching() {
        assert_eq!(
            aspect_fit(1920, 1080, 1000, 1000),
            Rect::new(0, 218, 1000, 563)
        );
    }

    #[test]
    fn sdl_keys_map_to_windows_virtual_keys_and_gfn_modifiers() {
        use sdl2::keyboard::{Mod, Scancode};

        assert_eq!(sdl_virtual_key(Scancode::W), Some(0x57));
        assert_eq!(sdl_virtual_key(Scancode::Escape), Some(0x1b));
        assert_eq!(sdl_virtual_key(Scancode::LCtrl), Some(0xa2));
        assert_eq!(
            sdl_modifiers(Scancode::W, Mod::LSHIFTMOD | Mod::LCTRLMOD),
            0x03
        );
        assert_eq!(sdl_modifiers(Scancode::LShift, Mod::LSHIFTMOD), 0);
    }

    #[test]
    fn adjacent_sdl_mouse_motion_is_coalesced_and_clamped() {
        let mut input = Vec::new();
        push_mouse_motion(&mut input, 10, -20);
        push_mouse_motion(&mut input, i32::MAX, -30);

        assert_eq!(
            input,
            vec![CapturedInput::MouseMove {
                delta_x: i16::MAX,
                delta_y: -50,
            }]
        );
    }
}
