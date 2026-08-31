use std::collections::{HashMap, VecDeque};
use std::ffi::c_void;
use std::mem::ManuallyDrop;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, ThreadId};

use ::windows::Win32::Foundation::{LUID, RECT};
use ::windows::Win32::Graphics::Direct3D11::{
    D3D11_BIND_RENDER_TARGET, D3D11_BIND_SHADER_RESOURCE, D3D11_TEX2D_VPIV, D3D11_TEX2D_VPOV,
    D3D11_TEXTURE2D_DESC, D3D11_USAGE_DEFAULT, D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE,
    D3D11_VIDEO_PROCESSOR_CONTENT_DESC, D3D11_VIDEO_PROCESSOR_FORMAT_SUPPORT_OUTPUT,
    D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC, D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC_0,
    D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC, D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC_0,
    D3D11_VIDEO_PROCESSOR_STREAM, D3D11_VIDEO_USAGE_OPTIMAL_SPEED, D3D11_VPIV_DIMENSION_TEXTURE2D,
    D3D11_VPOV_DIMENSION_TEXTURE2D, ID3D11Device, ID3D11DeviceContext, ID3D11Texture2D,
    ID3D11VideoContext, ID3D11VideoContext1, ID3D11VideoDevice, ID3D11VideoProcessor,
    ID3D11VideoProcessorEnumerator, ID3D11VideoProcessorEnumerator1, ID3D11VideoProcessorInputView,
    ID3D11VideoProcessorOutputView,
};
use ::windows::Win32::Graphics::Dxgi::Common::{
    DXGI_COLOR_SPACE_RGB_FULL_G22_NONE_P709, DXGI_COLOR_SPACE_YCBCR_FULL_G22_LEFT_P709,
    DXGI_COLOR_SPACE_YCBCR_STUDIO_G22_LEFT_P709, DXGI_FORMAT, DXGI_FORMAT_AYUV, DXGI_FORMAT_NV12,
    DXGI_FORMAT_P010, DXGI_FORMAT_R8G8B8A8_UNORM, DXGI_FORMAT_R10G10B10A2_UNORM, DXGI_FORMAT_Y410,
    DXGI_RATIONAL, DXGI_SAMPLE_DESC,
};
use ::windows::Win32::Graphics::Dxgi::IDXGIDevice;
use ::windows::Win32::Media::MediaFoundation::{
    IMFDXGIDeviceManager, MF_VERSION, MFCreateDXGIDeviceManager, MFSTARTUP_LITE, MFShutdown,
    MFStartup,
};
use ::windows::Win32::System::Com::{
    CO_MTA_USAGE_COOKIE, CoDecrementMTAUsage, CoIncrementMTAUsage,
};
use ::windows::core::{IUnknown, Interface};

use crate::queue::BoundedQueue;
use crate::{
    ADAPTIVE_VIDEO_QUEUE_CAPACITY, BackendError, BackendEvent, EncodedVideoFrame, PushOutcome,
    Subsystem, VideoChromaFormat, VideoFormat, VideoPixelFormat, WindowsDecoderMode,
};

use super::decoder::{DecodedVideoFrame, Decoder, DecoderDevice};

const MAX_DECODE_STEPS_PER_RECORD: usize = ADAPTIVE_VIDEO_QUEUE_CAPACITY * 2;
const MAX_FRAME_SLOTS: usize = 8;

/// Borrowed Qt D3D11 objects used by the embedded frame producer.
///
/// Both pointers must identify the D3D11 device and its immediate context from
/// Qt's adopted QRhi. The producer takes its own COM references and never
/// assumes ownership of either incoming reference.
#[derive(Debug, Clone, Copy)]
pub struct AdoptedD3d11Context {
    pub device: *mut c_void,
    pub immediate_context: *mut c_void,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum D3d11TextureFormat {
    Rgba8,
    Rgb10A2,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct D3d11RecordedFrame {
    pub texture: *mut c_void,
    pub texture_format: D3d11TextureFormat,
    pub width: u32,
    pub height: u32,
    pub frame_slot: u32,
    pub generation: u64,
    pub presentation_time_ns: u64,
}

#[derive(Clone)]
pub struct D3d11FrameSubmitter {
    encoded: Arc<BoundedQueue<EncodedVideoFrame>>,
    events: Arc<BoundedQueue<BackendEvent>>,
}

impl D3d11FrameSubmitter {
    pub fn submit_video(&self, frame: EncodedVideoFrame) -> Result<PushOutcome, BackendError> {
        frame.validate()?;
        let outcome = self
            .encoded
            .push_or_clear_on_overflow(frame)
            .map_err(|_| BackendError::WorkerDisconnected)?;
        if outcome == PushOutcome::DroppedOldest {
            let _ = self
                .events
                .push(BackendEvent::QueueOverflow(Subsystem::VideoDecode));
            let _ = self.events.push(BackendEvent::KeyFrameRequired);
        }
        Ok(outcome)
    }
}

struct EmbeddedMediaRuntime {
    mta_cookie: usize,
    media_foundation_started: bool,
}

impl EmbeddedMediaRuntime {
    fn initialize() -> Result<Self, String> {
        unsafe {
            let mta_cookie =
                CoIncrementMTAUsage().map_err(|error| format!("CoIncrementMTAUsage: {error}"))?;
            if let Err(error) = MFStartup(MF_VERSION, MFSTARTUP_LITE) {
                let _ = CoDecrementMTAUsage(mta_cookie);
                return Err(format!("MFStartup: {error}"));
            }
            Ok(Self {
                mta_cookie: mta_cookie.0 as usize,
                media_foundation_started: true,
            })
        }
    }
}

impl Drop for EmbeddedMediaRuntime {
    fn drop(&mut self) {
        unsafe {
            if self.media_foundation_started {
                let _ = MFShutdown();
            }
            let _ = CoDecrementMTAUsage(CO_MTA_USAGE_COOKIE(self.mta_cookie as *mut c_void));
        }
    }
}

struct FrameSlot {
    texture: ID3D11Texture2D,
    output_view: ID3D11VideoProcessorOutputView,
}

struct ProcessorResources {
    input_width: u32,
    input_height: u32,
    input_format: DXGI_FORMAT,
    output_width: u32,
    output_height: u32,
    output_format: DXGI_FORMAT,
    enumerator: ID3D11VideoProcessorEnumerator,
    processor: ID3D11VideoProcessor,
    input_views: HashMap<(usize, u32), ID3D11VideoProcessorInputView>,
    slots: Vec<FrameSlot>,
}

struct AdoptedResources {
    device: ID3D11Device,
    video_device: ID3D11VideoDevice,
    video_context: ID3D11VideoContext,
    video_context_1: Option<ID3D11VideoContext1>,
    manager: IMFDXGIDeviceManager,
    format: VideoFormat,
    generation: u64,
    processor: Option<ProcessorResources>,
}

impl AdoptedResources {
    unsafe fn new(adopted: AdoptedD3d11Context, format: VideoFormat) -> Result<Self, String> {
        if adopted.device.is_null() || adopted.immediate_context.is_null() {
            return Err("Qt supplied a null D3D11 device or immediate context".to_owned());
        }
        let device = unsafe { clone_interface::<ID3D11Device>(adopted.device)? };
        let context = unsafe { clone_interface::<ID3D11DeviceContext>(adopted.immediate_context)? };
        let context_device = unsafe {
            context
                .GetDevice()
                .map_err(|error| format!("ID3D11DeviceContext::GetDevice: {error}"))?
        };
        if com_identity(&device)? != com_identity(&context_device)? {
            return Err(
                "Qt D3D11 immediate context does not belong to the adopted device".to_owned(),
            );
        }
        let video_device = device
            .cast()
            .map_err(|error| format!("Qt D3D11 device has no video interface: {error}"))?;
        let video_context: ID3D11VideoContext = context
            .cast()
            .map_err(|error| format!("Qt immediate context has no video interface: {error}"))?;
        let video_context_1 = video_context.cast().ok();
        let mut reset_token = 0;
        let mut manager = None;
        unsafe {
            MFCreateDXGIDeviceManager(&mut reset_token, &mut manager)
                .map_err(|error| format!("MFCreateDXGIDeviceManager: {error}"))?;
        }
        let manager = manager.ok_or("MFCreateDXGIDeviceManager returned no manager")?;
        unsafe {
            manager
                .ResetDevice(&device, reset_token)
                .map_err(|error| format!("IMFDXGIDeviceManager::ResetDevice: {error}"))?;
        }
        Ok(Self {
            device,
            video_device,
            video_context,
            video_context_1,
            manager,
            format,
            generation: 0,
            processor: None,
        })
    }

    fn reconfigure(&mut self, format: VideoFormat) {
        if self.format != format {
            self.format = format;
            self.processor = None;
        }
    }

    fn reset_decoder_views(&mut self) {
        if let Some(processor) = self.processor.as_mut() {
            processor.input_views.clear();
        }
    }

    unsafe fn validate_adopted_context(&self, adopted: AdoptedD3d11Context) -> Result<(), String> {
        let device = unsafe { clone_interface::<ID3D11Device>(adopted.device)? };
        let context = unsafe { clone_interface::<ID3D11DeviceContext>(adopted.immediate_context)? };
        if com_identity(&device)? != com_identity(&self.device)?
            || com_identity(&context)? != com_identity(&self.video_context)?
        {
            return Err("D3D11 frame belongs to an older Qt graphics context".to_owned());
        }
        Ok(())
    }

    fn record(
        &mut self,
        frame_slot: u32,
        frame: &DecodedVideoFrame,
    ) -> Result<D3d11RecordedFrame, String> {
        let slot = usize::try_from(frame_slot)
            .ok()
            .filter(|slot| *slot < MAX_FRAME_SLOTS)
            .ok_or_else(|| format!("invalid D3D11 frame slot {frame_slot}"))?;
        let mut input_description = D3D11_TEXTURE2D_DESC::default();
        unsafe {
            frame.texture.GetDesc(&mut input_description);
        }
        let pixel_format = pixel_format_from_dxgi(input_description.Format).ok_or_else(|| {
            format!(
                "Media Foundation returned unsupported D3D11 texture format {}",
                input_description.Format.0
            )
        })?;
        if pixel_format != self.format.pixel_format
            || input_description.Width != self.format.width
            || input_description.Height != self.format.height
        {
            self.format = VideoFormat {
                width: input_description.Width,
                height: input_description.Height,
                pixel_format,
                chroma_format: chroma_format(pixel_format),
                ..self.format
            };
            self.processor = None;
        }
        let array_slice = decoder_array_slice(
            frame.subresource,
            input_description.MipLevels,
            input_description.ArraySize,
        )?;
        self.ensure_processor(
            input_description.Width,
            input_description.Height,
            input_description.Format,
            input_description.Width,
            input_description.Height,
        )?;
        let processor = self
            .processor
            .as_mut()
            .ok_or("embedded video processor is unavailable")?;
        let input_key = (frame.texture.as_raw() as usize, array_slice);
        let input_view = if let Some(view) = processor.input_views.get(&input_key) {
            view.clone()
        } else {
            let description = D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC {
                FourCC: 0,
                ViewDimension: D3D11_VPIV_DIMENSION_TEXTURE2D,
                Anonymous: D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC_0 {
                    Texture2D: D3D11_TEX2D_VPIV {
                        MipSlice: 0,
                        ArraySlice: array_slice,
                    },
                },
            };
            let mut view = None;
            unsafe {
                self.video_device
                    .CreateVideoProcessorInputView(
                        &frame.texture,
                        &processor.enumerator,
                        &description,
                        Some(&mut view),
                    )
                    .map_err(|error| format!("CreateVideoProcessorInputView: {error}"))?;
            }
            let view = view.ok_or("D3D11 returned no video processor input view")?;
            processor.input_views.insert(input_key, view.clone());
            view
        };
        let source = RECT {
            left: 0,
            top: 0,
            right: processor.input_width as i32,
            bottom: processor.input_height as i32,
        };
        let destination = RECT {
            left: 0,
            top: 0,
            right: processor.output_width as i32,
            bottom: processor.output_height as i32,
        };
        let output_view = processor.slots[slot].output_view.clone();
        unsafe {
            self.video_context.VideoProcessorSetStreamFrameFormat(
                &processor.processor,
                0,
                D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE,
            );
            self.video_context.VideoProcessorSetStreamSourceRect(
                &processor.processor,
                0,
                true,
                Some(&source),
            );
            self.video_context.VideoProcessorSetStreamDestRect(
                &processor.processor,
                0,
                true,
                Some(&destination),
            );
            self.video_context.VideoProcessorSetOutputTargetRect(
                &processor.processor,
                true,
                Some(&RECT {
                    left: 0,
                    top: 0,
                    right: processor.output_width as i32,
                    bottom: processor.output_height as i32,
                }),
            );
            let mut stream = D3D11_VIDEO_PROCESSOR_STREAM {
                Enable: true.into(),
                pInputSurface: ManuallyDrop::new(Some(input_view)),
                ..Default::default()
            };
            let result = self.video_context.VideoProcessorBlt(
                &processor.processor,
                &output_view,
                0,
                std::slice::from_ref(&stream),
            );
            ManuallyDrop::drop(&mut stream.pInputSurface);
            result.map_err(|error| format!("VideoProcessorBlt: {error}"))?;
        }
        self.generation = self.generation.wrapping_add(1).max(1);
        Ok(D3d11RecordedFrame {
            texture: processor.slots[slot].texture.as_raw(),
            texture_format: d3d11_texture_format(processor.output_format),
            width: processor.output_width,
            height: processor.output_height,
            frame_slot,
            generation: self.generation,
            presentation_time_ns: u64::try_from(frame.timestamp_100ns.max(0))
                .unwrap_or(0)
                .saturating_mul(100),
        })
    }

    fn ensure_processor(
        &mut self,
        input_width: u32,
        input_height: u32,
        input_format: DXGI_FORMAT,
        output_width: u32,
        output_height: u32,
    ) -> Result<(), String> {
        let output_format = output_dxgi_format(self.format.pixel_format);
        if self.processor.as_ref().is_some_and(|processor| {
            processor.input_width == input_width
                && processor.input_height == input_height
                && processor.input_format == input_format
                && processor.output_width == output_width
                && processor.output_height == output_height
                && processor.output_format == output_format
        }) {
            return Ok(());
        }
        let description = D3D11_VIDEO_PROCESSOR_CONTENT_DESC {
            InputFrameFormat: D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE,
            InputFrameRate: DXGI_RATIONAL {
                Numerator: self.format.frame_rate_numerator.get(),
                Denominator: self.format.frame_rate_denominator.get(),
            },
            InputWidth: input_width,
            InputHeight: input_height,
            OutputFrameRate: DXGI_RATIONAL {
                Numerator: self.format.frame_rate_numerator.get(),
                Denominator: self.format.frame_rate_denominator.get(),
            },
            OutputWidth: output_width,
            OutputHeight: output_height,
            Usage: D3D11_VIDEO_USAGE_OPTIMAL_SPEED,
        };
        let enumerator = unsafe {
            self.video_device
                .CreateVideoProcessorEnumerator(&description)
                .map_err(|error| format!("CreateVideoProcessorEnumerator: {error}"))?
        };
        validate_conversion(&enumerator, input_format, output_format, self.format)?;
        let processor = unsafe {
            self.video_device
                .CreateVideoProcessor(&enumerator, 0)
                .map_err(|error| format!("CreateVideoProcessor: {error}"))?
        };
        if let Some(context) = self.video_context_1.as_ref() {
            unsafe {
                context.VideoProcessorSetStreamColorSpace1(
                    &processor,
                    0,
                    input_color_space(self.format),
                );
                context.VideoProcessorSetOutputColorSpace1(
                    &processor,
                    DXGI_COLOR_SPACE_RGB_FULL_G22_NONE_P709,
                );
            }
        }
        let mut slots = Vec::with_capacity(MAX_FRAME_SLOTS);
        for _ in 0..MAX_FRAME_SLOTS {
            let description = frame_slot_description(output_width, output_height, output_format);
            let mut texture = None;
            unsafe {
                self.device
                    .CreateTexture2D(&description, None, Some(&mut texture))
                    .map_err(|error| format!("CreateTexture2D frame slot: {error}"))?;
            }
            let texture = texture.ok_or("D3D11 returned no frame-slot texture")?;
            let output_description = D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC {
                ViewDimension: D3D11_VPOV_DIMENSION_TEXTURE2D,
                Anonymous: D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC_0 {
                    Texture2D: D3D11_TEX2D_VPOV { MipSlice: 0 },
                },
            };
            let mut output_view = None;
            unsafe {
                self.video_device
                    .CreateVideoProcessorOutputView(
                        &texture,
                        &enumerator,
                        &output_description,
                        Some(&mut output_view),
                    )
                    .map_err(|error| format!("CreateVideoProcessorOutputView: {error}"))?;
            }
            slots.push(FrameSlot {
                texture,
                output_view: output_view.ok_or("D3D11 returned no frame-slot output view")?,
            });
        }
        self.processor = Some(ProcessorResources {
            input_width,
            input_height,
            input_format,
            output_width,
            output_height,
            output_format,
            enumerator,
            processor,
            input_views: HashMap::new(),
            slots,
        });
        Ok(())
    }
}

impl DecoderDevice for AdoptedResources {
    fn device_manager(&self) -> &IMFDXGIDeviceManager {
        &self.manager
    }

    fn adapter_luid(&self) -> Result<LUID, String> {
        unsafe {
            let dxgi_device: IDXGIDevice = self
                .device
                .cast()
                .map_err(|error| format!("Qt D3D11 DXGI device: {error}"))?;
            let adapter = dxgi_device
                .GetAdapter()
                .map_err(|error| format!("Qt D3D11 adapter: {error}"))?;
            Ok(adapter
                .GetDesc()
                .map_err(|error| format!("Qt D3D11 adapter description: {error}"))?
                .AdapterLuid)
        }
    }

    fn video_format(&self) -> VideoFormat {
        self.format
    }
}

struct D3d11Pipeline {
    owner_thread: ThreadId,
    mode: WindowsDecoderMode,
    resources: AdoptedResources,
    decoder: Option<Decoder>,
    decoded: VecDeque<DecodedVideoFrame>,
    encoded: Arc<BoundedQueue<EncodedVideoFrame>>,
    events: Arc<BoundedQueue<BackendEvent>>,
    _runtime: EmbeddedMediaRuntime,
}

unsafe impl Send for D3d11Pipeline {}

pub struct D3d11Frame {
    frame: DecodedVideoFrame,
    state: Arc<Mutex<D3d11Pipeline>>,
    sequence: u64,
}

unsafe impl Send for D3d11Frame {}
unsafe impl Sync for D3d11Frame {}

impl D3d11Frame {
    pub fn width(&self) -> u32 {
        let mut description = D3D11_TEXTURE2D_DESC::default();
        unsafe {
            self.frame.texture.GetDesc(&mut description);
        }
        description.Width
    }

    pub fn height(&self) -> u32 {
        let mut description = D3D11_TEXTURE2D_DESC::default();
        unsafe {
            self.frame.texture.GetDesc(&mut description);
        }
        description.Height
    }

    pub const fn sequence(&self) -> u64 {
        self.sequence
    }

    pub fn presentation_time_ns(&self) -> u64 {
        u64::try_from(self.frame.timestamp_100ns.max(0))
            .unwrap_or(0)
            .saturating_mul(100)
    }

    /// Converts this decoded surface into Qt's matching 8-bit or 10-bit RGB frame-slot target.
    ///
    /// # Safety
    ///
    /// `target.texture` must identify a live `ID3D11Texture2D` from the adopted
    /// Qt device. Recording must run on the render thread that created the
    /// producer.
    pub unsafe fn record(
        &self,
        adopted: AdoptedD3d11Context,
        frame_slot: u32,
    ) -> Result<D3d11RecordedFrame, BackendError> {
        let mut state = self
            .state
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        state.ensure_render_thread()?;
        unsafe { state.resources.validate_adopted_context(adopted) }.map_err(|message| {
            BackendError::DeviceLost {
                subsystem: Subsystem::VideoPresentation,
                message,
            }
        })?;
        state
            .resources
            .record(frame_slot, &self.frame)
            .map_err(|message| BackendError::DeviceLost {
                subsystem: Subsystem::VideoPresentation,
                message,
            })
    }
}

#[derive(Clone)]
pub struct D3d11FrameProducer {
    state: Arc<Mutex<D3d11Pipeline>>,
    sequence: Arc<AtomicU64>,
}

impl D3d11FrameProducer {
    /// Adopts Qt's D3D11 device and immediate context without creating a
    /// window, swap chain, SDL video subsystem, or presentation path.
    ///
    /// # Safety
    ///
    /// Both COM pointers must be valid for this call and identify live
    /// `ID3D11Device` and `ID3D11DeviceContext` interfaces. The context must be
    /// Qt's immediate context, and all producer methods except submission and
    /// event polling must remain on the creating render thread.
    pub unsafe fn new(
        adopted: AdoptedD3d11Context,
        format: VideoFormat,
        decoder_mode: WindowsDecoderMode,
    ) -> Result<(Self, D3d11FrameSubmitter), BackendError> {
        format.validate()?;
        let runtime = EmbeddedMediaRuntime::initialize().map_err(BackendError::Startup)?;
        let resources =
            unsafe { AdoptedResources::new(adopted, format) }.map_err(BackendError::Startup)?;
        let decoder = Decoder::new(&resources, format, decoder_mode)
            .map_err(|error| BackendError::Startup(format!("Media Foundation decoder: {error}")))?;
        let encoded = Arc::new(BoundedQueue::new(ADAPTIVE_VIDEO_QUEUE_CAPACITY));
        let events = Arc::new(BoundedQueue::new(64));
        let submitter = D3d11FrameSubmitter {
            encoded: Arc::clone(&encoded),
            events: Arc::clone(&events),
        };
        let state = Arc::new(Mutex::new(D3d11Pipeline {
            owner_thread: thread::current().id(),
            mode: decoder_mode,
            resources,
            decoder: Some(decoder),
            decoded: VecDeque::with_capacity(ADAPTIVE_VIDEO_QUEUE_CAPACITY),
            encoded,
            events,
            _runtime: runtime,
        }));
        Ok((
            Self {
                state,
                sequence: Arc::new(AtomicU64::new(0)),
            },
            submitter,
        ))
    }

    pub fn acquire_latest(&self) -> Result<Option<D3d11Frame>, BackendError> {
        let mut state = self
            .state
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        let frame = state.acquire_latest()?;
        Ok(frame.map(|frame| D3d11Frame {
            frame,
            state: Arc::clone(&self.state),
            sequence: self.sequence.fetch_add(1, Ordering::AcqRel) + 1,
        }))
    }

    pub fn try_event(&self) -> Option<BackendEvent> {
        self.state
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
            .events
            .try_pop()
    }
}

impl D3d11Pipeline {
    fn acquire_latest(&mut self) -> Result<Option<DecodedVideoFrame>, BackendError> {
        self.ensure_render_thread()?;
        for _ in 0..MAX_DECODE_STEPS_PER_RECORD {
            let decoder = self
                .decoder
                .as_mut()
                .ok_or_else(|| BackendError::DeviceLost {
                    subsystem: Subsystem::VideoDecode,
                    message: "embedded decoder is unavailable".to_owned(),
                })?;
            let produced = decoder
                .poll_output(&mut self.decoded, &self.events)
                .map_err(|error| BackendError::DeviceLost {
                    subsystem: Subsystem::VideoDecode,
                    message: error,
                })?;
            let decoded_format = decoder.format();
            self.resources.reconfigure(decoded_format);
            let mut submitted = false;
            while self.decoder.as_ref().is_some_and(Decoder::wants_input) {
                let Some(frame) = self.encoded.try_pop() else {
                    break;
                };
                submitted = true;
                if frame.reset_decoder {
                    self.reset_decoder()
                        .map_err(|message| BackendError::DeviceLost {
                            subsystem: Subsystem::VideoDecode,
                            message,
                        })?;
                }
                self.decoder
                    .as_mut()
                    .expect("decoder restored before submission")
                    .submit(frame)
                    .map_err(|message| BackendError::DeviceLost {
                        subsystem: Subsystem::VideoDecode,
                        message,
                    })?;
            }
            if produced == 0 && !submitted {
                break;
            }
        }
        let Some(frame) = self.decoded.pop_back() else {
            return Ok(None);
        };
        if !self.decoded.is_empty() {
            self.decoded.clear();
            let _ = self
                .events
                .push(BackendEvent::QueueOverflow(Subsystem::VideoPresentation));
        }
        Ok(Some(frame))
    }

    fn reset_decoder(&mut self) -> Result<(), String> {
        if let Some(mut decoder) = self.decoder.take() {
            decoder.stop();
        }
        self.decoded.clear();
        self.resources.reset_decoder_views();
        self.decoder = Some(Decoder::new(
            &self.resources,
            self.resources.format,
            self.mode,
        )?);
        Ok(())
    }

    fn ensure_render_thread(&self) -> Result<(), BackendError> {
        if thread::current().id() == self.owner_thread {
            Ok(())
        } else {
            Err(BackendError::InvalidConfig(
                "embedded D3D11 frame production must stay on Qt's render thread".to_owned(),
            ))
        }
    }
}

impl Drop for D3d11Pipeline {
    fn drop(&mut self) {
        self.encoded.close();
        if let Some(mut decoder) = self.decoder.take() {
            decoder.stop();
        }
    }
}

unsafe fn clone_interface<T: Interface>(pointer: *mut c_void) -> Result<T, String> {
    if pointer.is_null() {
        return Err("cannot adopt a null COM interface".to_owned());
    }
    let borrowed = ManuallyDrop::new(unsafe { T::from_raw(pointer) });
    Ok((*borrowed).clone())
}

fn com_identity<T: Interface>(interface: &T) -> Result<usize, String> {
    interface
        .cast::<IUnknown>()
        .map(|unknown| unknown.as_raw() as usize)
        .map_err(|error| error.to_string())
}

fn decoder_array_slice(subresource: u32, mip_levels: u32, array_size: u32) -> Result<u32, String> {
    let mip_levels = mip_levels.max(1);
    let array_size = array_size.max(1);
    let total = mip_levels
        .checked_mul(array_size)
        .ok_or("decoder texture subresource count overflowed")?;
    if subresource >= total {
        return Err(format!(
            "decoder subresource {subresource} exceeds texture layout ({mip_levels} mips, {array_size} slices)"
        ));
    }
    Ok(subresource / mip_levels)
}

fn frame_slot_description(width: u32, height: u32, format: DXGI_FORMAT) -> D3D11_TEXTURE2D_DESC {
    D3D11_TEXTURE2D_DESC {
        Width: width,
        Height: height,
        MipLevels: 1,
        ArraySize: 1,
        Format: format,
        SampleDesc: DXGI_SAMPLE_DESC {
            Count: 1,
            Quality: 0,
        },
        Usage: D3D11_USAGE_DEFAULT,
        BindFlags: (D3D11_BIND_SHADER_RESOURCE | D3D11_BIND_RENDER_TARGET).0 as u32,
        CPUAccessFlags: 0,
        MiscFlags: 0,
    }
}

fn validate_conversion(
    enumerator: &ID3D11VideoProcessorEnumerator,
    input_format: DXGI_FORMAT,
    output_format: DXGI_FORMAT,
    format: VideoFormat,
) -> Result<(), String> {
    unsafe {
        let support = enumerator
            .CheckVideoProcessorFormat(output_format)
            .map_err(|error| format!("query RGB video-processor output: {error}"))?;
        if support & D3D11_VIDEO_PROCESSOR_FORMAT_SUPPORT_OUTPUT.0 as u32 == 0 {
            return Err(format!(
                "D3D11 video processor does not support output format {}",
                output_format.0
            ));
        }
        if let Ok(enumerator_1) = enumerator.cast::<ID3D11VideoProcessorEnumerator1>() {
            let supported = enumerator_1
                .CheckVideoProcessorFormatConversion(
                    input_format,
                    input_color_space(format),
                    output_format,
                    DXGI_COLOR_SPACE_RGB_FULL_G22_NONE_P709,
                )
                .map_err(|error| format!("query D3D11 embedded conversion: {error}"))?;
            if !supported.as_bool() {
                return Err(format!(
                    "D3D11 driver rejects embedded video conversion {} -> {}",
                    input_format.0, output_format.0
                ));
            }
        }
    }
    Ok(())
}

fn output_dxgi_format(format: VideoPixelFormat) -> DXGI_FORMAT {
    match format {
        VideoPixelFormat::P010 | VideoPixelFormat::Y410 => DXGI_FORMAT_R10G10B10A2_UNORM,
        VideoPixelFormat::Nv12 | VideoPixelFormat::Ayuv => DXGI_FORMAT_R8G8B8A8_UNORM,
    }
}

fn d3d11_texture_format(format: DXGI_FORMAT) -> D3d11TextureFormat {
    if format == DXGI_FORMAT_R10G10B10A2_UNORM {
        D3d11TextureFormat::Rgb10A2
    } else {
        D3d11TextureFormat::Rgba8
    }
}

fn input_color_space(
    format: VideoFormat,
) -> ::windows::Win32::Graphics::Dxgi::Common::DXGI_COLOR_SPACE_TYPE {
    if format.full_range {
        DXGI_COLOR_SPACE_YCBCR_FULL_G22_LEFT_P709
    } else {
        DXGI_COLOR_SPACE_YCBCR_STUDIO_G22_LEFT_P709
    }
}

fn pixel_format_from_dxgi(format: DXGI_FORMAT) -> Option<VideoPixelFormat> {
    match format {
        DXGI_FORMAT_NV12 => Some(VideoPixelFormat::Nv12),
        DXGI_FORMAT_P010 => Some(VideoPixelFormat::P010),
        DXGI_FORMAT_AYUV => Some(VideoPixelFormat::Ayuv),
        DXGI_FORMAT_Y410 => Some(VideoPixelFormat::Y410),
        _ => None,
    }
}

fn chroma_format(format: VideoPixelFormat) -> VideoChromaFormat {
    match format {
        VideoPixelFormat::Nv12 | VideoPixelFormat::P010 => VideoChromaFormat::Cs420,
        VideoPixelFormat::Ayuv | VideoPixelFormat::Y410 => VideoChromaFormat::Cs444,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decoder_subresource_preserves_array_slice() {
        assert_eq!(decoder_array_slice(0, 1, 8), Ok(0));
        assert_eq!(decoder_array_slice(5, 1, 8), Ok(5));
        assert_eq!(decoder_array_slice(7, 1, 8), Ok(7));
        assert_eq!(decoder_array_slice(6, 3, 4), Ok(2));
        assert!(decoder_array_slice(12, 3, 4).is_err());
    }

    #[test]
    fn all_decoder_formats_map_to_video_processor_inputs() {
        let cases = [
            (
                DXGI_FORMAT_NV12,
                VideoPixelFormat::Nv12,
                VideoChromaFormat::Cs420,
            ),
            (
                DXGI_FORMAT_P010,
                VideoPixelFormat::P010,
                VideoChromaFormat::Cs420,
            ),
            (
                DXGI_FORMAT_AYUV,
                VideoPixelFormat::Ayuv,
                VideoChromaFormat::Cs444,
            ),
            (
                DXGI_FORMAT_Y410,
                VideoPixelFormat::Y410,
                VideoChromaFormat::Cs444,
            ),
        ];
        for (dxgi, pixel, chroma) in cases {
            let mapped = pixel_format_from_dxgi(dxgi).expect("supported decoder format");
            assert_eq!(mapped, pixel);
            assert_eq!(chroma_format(mapped), chroma);
        }
        assert_eq!(DXGI_FORMAT_R8G8B8A8_UNORM.0, 28);
    }

    #[test]
    fn frame_slots_preserve_source_bit_depth_in_shader_readable_render_targets() {
        let description = frame_slot_description(1920, 1080, DXGI_FORMAT_R8G8B8A8_UNORM);
        assert_eq!(description.Width, 1920);
        assert_eq!(description.Height, 1080);
        assert_eq!(description.Format, DXGI_FORMAT_R8G8B8A8_UNORM);
        assert_ne!(
            description.BindFlags & D3D11_BIND_SHADER_RESOURCE.0 as u32,
            0
        );
        assert_ne!(description.BindFlags & D3D11_BIND_RENDER_TARGET.0 as u32, 0);

        let ten_bit = frame_slot_description(2560, 1440, DXGI_FORMAT_R10G10B10A2_UNORM);
        assert_eq!(ten_bit.Format, DXGI_FORMAT_R10G10B10A2_UNORM);
        assert_eq!(output_dxgi_format(VideoPixelFormat::P010), ten_bit.Format);
        assert_eq!(output_dxgi_format(VideoPixelFormat::Y410), ten_bit.Format);
        assert_eq!(
            d3d11_texture_format(ten_bit.Format),
            D3d11TextureFormat::Rgb10A2
        );
    }

    #[test]
    fn embedded_module_has_no_window_swapchain_sdl_or_present_path() {
        let source = include_str!("embedded.rs");
        for forbidden in [
            concat!("Create", "Window"),
            concat!("Create", "SwapChain"),
            concat!(".Pre", "sent("),
            concat!("sdl", "2::"),
        ] {
            assert!(
                !source.contains(forbidden),
                "embedded producer contains forbidden presentation token {forbidden}"
            );
        }
    }

    #[test]
    fn encoded_submitter_is_safe_to_move_to_the_transport_thread() {
        fn assert_send_sync<T: Send + Sync>() {}
        assert_send_sync::<D3d11FrameSubmitter>();
        assert_send_sync::<D3d11FrameProducer>();
        assert_send_sync::<D3d11Frame>();
    }
}
