use std::ffi::c_void;
use std::ptr::{self, NonNull};
use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

use objc2_core_foundation::{
    CFData, CFDictionary, CFNumber, CFNumberType, CFRetained, CFString, CFType, kCFBooleanTrue,
    kCFTypeDictionaryKeyCallBacks, kCFTypeDictionaryValueCallBacks,
};
use objc2_core_media::{
    CMBlockBuffer, CMFormatDescription, CMSampleBuffer, CMSampleTimingInfo, CMTime,
    CMVideoFormatDescriptionCreate, CMVideoFormatDescriptionCreateFromH264ParameterSets,
    CMVideoFormatDescriptionCreateFromHEVCParameterSets,
    kCMFormatDescriptionExtension_BitsPerComponent,
    kCMFormatDescriptionExtension_SampleDescriptionExtensionAtoms, kCMTimeInvalid,
    kCMVideoCodecType_AV1,
};
use objc2_core_video::{
    CVImageBuffer, CVPixelBufferGetPixelFormatType, kCVImageBufferColorPrimaries_EBU_3213,
    kCVImageBufferColorPrimaries_ITU_R_709_2, kCVImageBufferColorPrimaries_ITU_R_2020,
    kCVImageBufferColorPrimaries_SMPTE_C, kCVImageBufferColorPrimariesKey,
    kCVImageBufferTransferFunction_ITU_R_709_2, kCVImageBufferTransferFunction_ITU_R_2100_HLG,
    kCVImageBufferTransferFunction_SMPTE_ST_2084_PQ, kCVImageBufferTransferFunction_sRGB,
    kCVImageBufferTransferFunctionKey, kCVImageBufferYCbCrMatrix_ITU_R_601_4,
    kCVImageBufferYCbCrMatrix_ITU_R_709_2, kCVImageBufferYCbCrMatrix_ITU_R_2020,
    kCVImageBufferYCbCrMatrixKey, kCVPixelBufferIOSurfacePropertiesKey,
    kCVPixelBufferMetalCompatibilityKey, kCVPixelBufferPixelFormatTypeKey,
    kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
    kCVPixelFormatType_420YpCbCr10BiPlanarFullRange,
    kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange,
};
use objc2_video_toolbox::{
    VTDecodeFrameFlags, VTDecodeInfoFlags, VTDecompressionOutputCallbackRecord,
    VTDecompressionSession, VTSessionSetProperty, kVTDecompressionPropertyKey_RealTime,
    kVTVideoDecoderSpecification_RequireHardwareAcceleratedVideoDecoder,
};

use crate::failure::{BackendSubsystem, FailureReporter};
use crate::format::{
    Av1Format, FrameTiming, H264Format, H265Format, MetalTextureColorSpace, VideoBitDepth,
    VideoColorSpace, VideoFormat, VideoTransferFunction,
};
use crate::frame_order::FrameOrder;
use crate::queue::{BoundedQueue, PushResult};

use super::mailbox::LatestMailbox;
use super::{BackendError, Counters};

#[derive(Clone)]
pub(super) struct DecodedFrame {
    pub(super) image: CFRetained<CVImageBuffer>,
    pub(super) color_space: VideoColorSpace,
    pub(super) minimum_frame_duration_seconds: f64,
    pub(super) timestamp_100ns: i64,
    pub(super) frame_index: Option<u32>,
    pub(super) texture_color_space: MetalTextureColorSpace,
}

struct SubmittedFrame {
    order: u64,
    frame_index: Option<u32>,
}

// The callback retains the CVImageBuffer and no code mutates it after publication to the queue.
unsafe impl Send for DecodedFrame {}
unsafe impl Sync for DecodedFrame {}

#[derive(Clone)]
pub(super) enum DecodedFrameOutput {
    PresentationQueue(Arc<BoundedQueue<DecodedFrame>>),
    EmbeddedMailbox {
        mailbox: Arc<LatestMailbox<DecodedFrame>>,
        frame_available: Option<Arc<dyn Fn() + Send + Sync>>,
    },
}

impl DecodedFrameOutput {
    fn publish(&self, frame: DecodedFrame) -> bool {
        match self {
            Self::PresentationQueue(queue) => matches!(
                queue.push_drop_oldest(frame),
                PushResult::Replaced(_) | PushResult::Closed(_)
            ),
            Self::EmbeddedMailbox { mailbox, .. } => mailbox.replace(frame),
        }
    }

    fn notify(&self) {
        if let Self::EmbeddedMailbox {
            frame_available: Some(frame_available),
            ..
        } = self
        {
            frame_available();
        }
    }

    pub(super) fn clear(&self) -> usize {
        match self {
            Self::PresentationQueue(queue) => queue.clear(),
            Self::EmbeddedMailbox { mailbox, .. } => usize::from(mailbox.clear()),
        }
    }
}

struct InFlight {
    count: AtomicUsize,
    maximum: usize,
}

impl InFlight {
    fn try_acquire(&self) -> bool {
        self.count
            .fetch_update(Ordering::AcqRel, Ordering::Acquire, |count| {
                (count < self.maximum).then_some(count + 1)
            })
            .is_ok()
    }

    fn release(&self) {
        let previous = self.count.fetch_sub(1, Ordering::AcqRel);
        debug_assert!(previous > 0);
    }
}

struct CallbackContext {
    output: DecodedFrameOutput,
    counters: Arc<Counters>,
    failures: Arc<FailureReporter>,
    in_flight: Arc<InFlight>,
    color_space: VideoColorSpace,
    transfer_function: VideoTransferFunction,
    bit_depth: VideoBitDepth,
    frame_order: FrameOrder,
}

pub(super) struct VideoDecoder {
    session: Option<CFRetained<VTDecompressionSession>>,
    format_description: CFRetained<CMFormatDescription>,
    callback_context: Box<CallbackContext>,
    in_flight: Arc<InFlight>,
}

// VTDecompressionSession has no thread affinity. Shared owns VideoDecoder behind a Mutex, so
// decode, reconfiguration, and invalidation are serialized even when StreamSink moves threads.
unsafe impl Send for VideoDecoder {}

pub(super) fn probe_format(format: &VideoFormat) -> bool {
    VideoDecoder::new(
        format,
        DecodedFrameOutput::EmbeddedMailbox {
            mailbox: Arc::new(LatestMailbox::new()),
            frame_available: None,
        },
        Arc::new(Counters::default()),
        Arc::new(FailureReporter::default()),
        1,
    )
    .is_ok()
}

impl VideoDecoder {
    pub(super) fn new(
        format: &VideoFormat,
        output: DecodedFrameOutput,
        counters: Arc<Counters>,
        failures: Arc<FailureReporter>,
        maximum_in_flight: usize,
    ) -> Result<Self, BackendError> {
        let format_description = create_format_description(format)?;
        let bitstream_depth =
            unsafe { format_description.extension(kCMFormatDescriptionExtension_BitsPerComponent) }
                .and_then(|value| value.downcast_ref::<CFNumber>().and_then(CFNumber::as_i32));
        let bit_depth = format.destination_bit_depth(bitstream_depth)?;
        if format.transfer_function() != VideoTransferFunction::Sdr
            && (bit_depth != VideoBitDepth::Ten
                || matches!(output, DecodedFrameOutput::PresentationQueue(_)))
        {
            return Err(BackendError::Metal(
                "HDR requires ten-bit embedded Metal output".into(),
            ));
        }
        let in_flight = Arc::new(InFlight {
            count: AtomicUsize::new(0),
            maximum: maximum_in_flight,
        });
        let mut callback_context = Box::new(CallbackContext {
            output,
            counters,
            failures,
            in_flight: Arc::clone(&in_flight),
            color_space: format.color_space(),
            transfer_function: format.transfer_function(),
            bit_depth,
            frame_order: FrameOrder::default(),
        });
        let callback = VTDecompressionOutputCallbackRecord {
            decompressionOutputCallback: Some(decompression_callback),
            decompressionOutputRefCon: (&mut *callback_context as *mut CallbackContext).cast(),
        };

        let pixel_format = match bit_depth {
            VideoBitDepth::Eight => kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
            VideoBitDepth::Ten => kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange,
        } as i32;
        let pixel_format_number = unsafe {
            CFNumber::new(
                None,
                CFNumberType::SInt32Type,
                (&pixel_format as *const i32).cast(),
            )
        }
        .ok_or_else(|| BackendError::Metal("failed to create CV pixel format number".into()))?;
        let empty_properties = make_dictionary(&[])?;
        let true_value = unsafe { kCFBooleanTrue }.ok_or_else(|| {
            BackendError::Metal("CoreFoundation true value is unavailable".into())
        })?;
        let pixel_format_key = unsafe { kCVPixelBufferPixelFormatTypeKey };
        let metal_compatibility_key = unsafe { kCVPixelBufferMetalCompatibilityKey };
        let io_surface_properties_key = unsafe { kCVPixelBufferIOSurfacePropertiesKey };
        let hardware_decoder_key =
            unsafe { kVTVideoDecoderSpecification_RequireHardwareAcceleratedVideoDecoder };
        let mut destination_entries = vec![
            (cf_ptr(metal_compatibility_key), cf_ptr(true_value)),
            (
                cf_ptr(io_surface_properties_key),
                cf_ptr(&*empty_properties),
            ),
        ];
        destination_entries.insert(0, (cf_ptr(pixel_format_key), cf_ptr(&*pixel_format_number)));
        let destination_attributes = make_dictionary(&destination_entries)?;
        let decoder_specification =
            make_dictionary(&[(cf_ptr(hardware_decoder_key), cf_ptr(true_value))])?;

        let mut session_ptr = ptr::null_mut();
        let status = unsafe {
            VTDecompressionSession::create(
                None,
                &format_description,
                Some(&decoder_specification),
                Some(&destination_attributes),
                &callback,
                NonNull::from(&mut session_ptr),
            )
        };
        check_status("VTDecompressionSessionCreate", status)?;
        let session_ptr = NonNull::new(session_ptr).ok_or(BackendError::AppleApi {
            api: "VTDecompressionSessionCreate",
            status: -1,
        })?;
        let session = unsafe { CFRetained::from_raw(session_ptr) };
        let realtime_status = unsafe {
            VTSessionSetProperty(
                session.as_ref(),
                kVTDecompressionPropertyKey_RealTime,
                Some(true_value.as_ref()),
            )
        };
        if realtime_status != 0 {
            eprintln!(
                "VideoToolbox declined the real-time decode hint: OSStatus {realtime_status}"
            );
        }

        Ok(Self {
            session: Some(session),
            format_description,
            callback_context,
            in_flight,
        })
    }

    pub(super) fn submit(
        &self,
        avcc_access_unit: &[u8],
        timing: FrameTiming,
    ) -> Result<bool, BackendError> {
        if !self.in_flight.try_acquire() {
            return Ok(false);
        }
        let result = self.submit_acquired(avcc_access_unit, timing);
        if result.is_err() {
            self.in_flight.release();
            self.callback_context
                .counters
                .video_decode_errors
                .fetch_add(1, Ordering::Relaxed);
            let status = match &result {
                Err(BackendError::AppleApi { status, .. }) => Some(*status),
                _ => None,
            };
            self.callback_context.failures.video_decode_failed(status);
        }
        result.map(|()| true)
    }

    fn submit_acquired(
        &self,
        avcc_access_unit: &[u8],
        timing: FrameTiming,
    ) -> Result<(), BackendError> {
        let mut block_ptr = ptr::null_mut();
        let status = unsafe {
            CMBlockBuffer::create_with_memory_block(
                None,
                ptr::null_mut(),
                avcc_access_unit.len(),
                None,
                ptr::null(),
                0,
                avcc_access_unit.len(),
                0,
                NonNull::from(&mut block_ptr),
            )
        };
        check_status("CMBlockBufferCreateWithMemoryBlock", status)?;
        let block_ptr = NonNull::new(block_ptr).ok_or(BackendError::AppleApi {
            api: "CMBlockBufferCreateWithMemoryBlock",
            status: -1,
        })?;
        let block = unsafe { CFRetained::from_raw(block_ptr) };
        let source = NonNull::new(avcc_access_unit.as_ptr().cast_mut().cast::<c_void>())
            .expect("validated AVCC access unit is not empty");
        let status =
            unsafe { CMBlockBuffer::replace_data_bytes(source, &block, 0, avcc_access_unit.len()) };
        check_status("CMBlockBufferReplaceDataBytes", status)?;

        let sample_timing = CMSampleTimingInfo {
            duration: unsafe { CMTime::new(timing.duration_value, timing.timescale) },
            presentationTimeStamp: unsafe {
                CMTime::new(timing.presentation_value, timing.timescale)
            },
            decodeTimeStamp: unsafe { kCMTimeInvalid },
        };
        let sample_size = avcc_access_unit.len();
        let mut sample_ptr = ptr::null_mut();
        let status = unsafe {
            CMSampleBuffer::create_ready(
                None,
                Some(&block),
                Some(&self.format_description),
                1,
                1,
                &sample_timing,
                1,
                &sample_size,
                NonNull::from(&mut sample_ptr),
            )
        };
        check_status("CMSampleBufferCreateReady", status)?;
        let sample_ptr = NonNull::new(sample_ptr).ok_or(BackendError::AppleApi {
            api: "CMSampleBufferCreateReady",
            status: -1,
        })?;
        let sample = unsafe { CFRetained::from_raw(sample_ptr) };
        let session = self.session.as_ref().ok_or(BackendError::Stopped)?;
        let submission = Box::into_raw(Box::new(SubmittedFrame {
            order: self.callback_context.frame_order.submit(),
            frame_index: timing.frame_index,
        }));
        let status = unsafe {
            session.decode_frame(
                &sample,
                VTDecodeFrameFlags::Frame_EnableAsynchronousDecompression,
                submission.cast(),
                ptr::null_mut(),
            )
        };
        if status != 0 {
            drop(unsafe { Box::from_raw(submission) });
        }
        check_status("VTDecompressionSessionDecodeFrame", status)
    }
}

impl Drop for VideoDecoder {
    fn drop(&mut self) {
        if let Some(session) = self.session.take() {
            let _ = unsafe { session.wait_for_asynchronous_frames() };
            unsafe { session.invalidate() };
            drop(session);
        }
        debug_assert_eq!(self.in_flight.count.load(Ordering::Acquire), 0);
        let _ = &self.callback_context;
    }
}

unsafe extern "C-unwind" fn decompression_callback(
    output_refcon: *mut c_void,
    source_refcon: *mut c_void,
    status: i32,
    _info_flags: VTDecodeInfoFlags,
    image_buffer: *mut CVImageBuffer,
    presentation_time_stamp: CMTime,
    presentation_duration: CMTime,
) {
    let Some(submission) = NonNull::new(source_refcon.cast::<SubmittedFrame>()) else {
        return;
    };
    let submission = unsafe { Box::from_raw(submission.as_ptr()) };
    let Some(context) = NonNull::new(output_refcon.cast::<CallbackContext>()) else {
        return;
    };
    let context = unsafe { context.as_ref() };
    if status == 0 {
        if let Some(image_buffer) = NonNull::new(image_buffer) {
            let image = unsafe { CFRetained::retain(image_buffer) };
            let pixel_format = CVPixelBufferGetPixelFormatType(&image);
            if context.bit_depth == VideoBitDepth::Ten
                && pixel_format != kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange
                && pixel_format != kCVPixelFormatType_420YpCbCr10BiPlanarFullRange
            {
                context
                    .counters
                    .video_decode_errors
                    .fetch_add(1, Ordering::Relaxed);
                context.failures.report_fatal(
                    BackendSubsystem::VideoToolbox,
                    format!("VideoToolbox did not preserve ten-bit output: pixel format {pixel_format:#010x}"),
                );
                context.in_flight.release();
                return;
            }
            let color_space = unsafe {
                decoded_color_space(
                    image
                        .attachment(kCVImageBufferYCbCrMatrixKey, ptr::null_mut())
                        .as_deref(),
                    image
                        .attachment(kCVImageBufferTransferFunctionKey, ptr::null_mut())
                        .as_deref(),
                    image
                        .attachment(kCVImageBufferColorPrimariesKey, ptr::null_mut())
                        .as_deref(),
                    context.color_space,
                    context.transfer_function,
                )
            };
            let (color_space, texture_color_space) = match color_space {
                Ok(color_space) => color_space,
                Err(error) => {
                    context
                        .counters
                        .video_decode_errors
                        .fetch_add(1, Ordering::Relaxed);
                    context
                        .failures
                        .report_fatal(BackendSubsystem::VideoToolbox, error.to_string());
                    context.in_flight.release();
                    return;
                }
            };
            if texture_color_space != MetalTextureColorSpace::Sdr709
                && (pixel_format != kCVPixelFormatType_420YpCbCr10BiPlanarVideoRange
                    && pixel_format != kCVPixelFormatType_420YpCbCr10BiPlanarFullRange
                    || matches!(context.output, DecodedFrameOutput::PresentationQueue(_)))
            {
                context
                    .counters
                    .video_decode_errors
                    .fetch_add(1, Ordering::Relaxed);
                context.failures.report_fatal(
                    BackendSubsystem::VideoToolbox,
                    "VideoToolbox HDR requires ten-bit embedded Metal output".into(),
                );
                context.in_flight.release();
                return;
            }
            let frame = DecodedFrame {
                image,
                color_space,
                minimum_frame_duration_seconds: frame_duration_seconds(presentation_duration),
                timestamp_100ns: time_to_100ns(presentation_time_stamp),
                frame_index: submission.frame_index,
                texture_color_space,
            };
            context
                .counters
                .video_decoded
                .fetch_add(1, Ordering::Relaxed);
            context.failures.video_decode_succeeded();
            let published = context
                .frame_order
                .publish(submission.order, || context.output.publish(frame));
            if published != Some(false) {
                context
                    .counters
                    .video_frames_dropped
                    .fetch_add(1, Ordering::Relaxed);
                context
                    .counters
                    .video_decoded_queue_dropped
                    .fetch_add(1, Ordering::Relaxed);
            }
            if published.is_some() {
                context.output.notify();
            }
        } else {
            context
                .counters
                .video_decode_errors
                .fetch_add(1, Ordering::Relaxed);
            context.failures.video_decode_failed(None);
        }
    } else {
        context
            .counters
            .video_decode_errors
            .fetch_add(1, Ordering::Relaxed);
        context.failures.video_decode_failed(Some(status));
    }
    context.in_flight.release();
}

fn decoded_color_space(
    matrix: Option<&CFType>,
    transfer: Option<&CFType>,
    primaries: Option<&CFType>,
    fallback: VideoColorSpace,
    fallback_transfer: VideoTransferFunction,
) -> Result<(VideoColorSpace, MetalTextureColorSpace), BackendError> {
    let mut color_space = fallback;
    if let Some(matrix) = color_attachment(matrix, "YCbCr matrix")? {
        color_space = if matrix == unsafe { kCVImageBufferYCbCrMatrix_ITU_R_601_4 } {
            VideoColorSpace::Bt601
        } else if matrix == unsafe { kCVImageBufferYCbCrMatrix_ITU_R_709_2 } {
            VideoColorSpace::Bt709
        } else if matrix == unsafe { kCVImageBufferYCbCrMatrix_ITU_R_2020 } {
            VideoColorSpace::Bt2020
        } else {
            return Err(BackendError::Metal(format!(
                "unsupported VideoToolbox YCbCr matrix: {matrix}"
            )));
        };
    }
    let mut transfer_function = fallback_transfer;
    if let Some(transfer) = color_attachment(transfer, "transfer function")? {
        transfer_function =
            if transfer == unsafe { kCVImageBufferTransferFunction_SMPTE_ST_2084_PQ } {
                VideoTransferFunction::Pq
            } else if transfer == unsafe { kCVImageBufferTransferFunction_ITU_R_2100_HLG } {
                VideoTransferFunction::Hlg
            } else if unsafe {
                [
                    kCVImageBufferTransferFunction_ITU_R_709_2,
                    kCVImageBufferTransferFunction_sRGB,
                ]
            }
            .contains(&transfer)
            {
                VideoTransferFunction::Sdr
            } else {
                return Err(BackendError::Metal(format!(
                    "unsupported VideoToolbox transfer function: {transfer}"
                )));
            };
    }
    let mut bt2020_primaries = fallback_transfer != VideoTransferFunction::Sdr;
    if let Some(primaries) = color_attachment(primaries, "color primaries")? {
        bt2020_primaries = if primaries == unsafe { kCVImageBufferColorPrimaries_ITU_R_2020 } {
            true
        } else if unsafe {
            [
                kCVImageBufferColorPrimaries_ITU_R_709_2,
                kCVImageBufferColorPrimaries_EBU_3213,
                kCVImageBufferColorPrimaries_SMPTE_C,
            ]
        }
        .contains(&primaries)
        {
            false
        } else {
            return Err(BackendError::Metal(format!(
                "unsupported VideoToolbox color primaries: {primaries}"
            )));
        };
    }
    let texture_color_space = match (transfer_function, bt2020_primaries) {
        (VideoTransferFunction::Sdr, false) => MetalTextureColorSpace::Sdr709,
        (VideoTransferFunction::Pq, true) => MetalTextureColorSpace::Pq2020,
        (VideoTransferFunction::Hlg, true) => MetalTextureColorSpace::Hlg2020,
        _ => {
            return Err(BackendError::Metal(
                "unsupported VideoToolbox transfer/primaries combination".into(),
            ));
        }
    };
    Ok((color_space, texture_color_space))
}

fn color_attachment<'a>(
    attachment: Option<&'a CFType>,
    name: &str,
) -> Result<Option<&'a CFString>, BackendError> {
    attachment
        .map(|value| {
            value.downcast_ref::<CFString>().ok_or_else(|| {
                BackendError::Metal(format!(
                    "VideoToolbox returned an invalid {name} attachment"
                ))
            })
        })
        .transpose()
}

fn frame_duration_seconds(duration: CMTime) -> f64 {
    if duration.value > 0 && duration.timescale > 0 {
        (duration.value as f64 / f64::from(duration.timescale)).clamp(1.0 / 240.0, 1.0 / 24.0)
    } else {
        1.0 / 60.0
    }
}

fn time_to_100ns(time: CMTime) -> i64 {
    if time.timescale <= 0 {
        return 0;
    }
    i128::from(time.value)
        .saturating_mul(10_000_000)
        .checked_div(i128::from(time.timescale))
        .and_then(|value| i64::try_from(value).ok())
        .unwrap_or_else(|| {
            if time.value.is_negative() {
                i64::MIN
            } else {
                i64::MAX
            }
        })
}

fn create_format_description(
    format: &VideoFormat,
) -> Result<CFRetained<CMFormatDescription>, BackendError> {
    match format {
        VideoFormat::H264(format) => create_h264_format_description(format),
        VideoFormat::H265(format) => create_h265_format_description(format),
        VideoFormat::Av1(format) => create_av1_format_description(format),
    }
}

fn create_av1_format_description(
    format: &Av1Format,
) -> Result<CFRetained<CMFormatDescription>, BackendError> {
    let atom_name = CFString::from_static_str("av1C");
    let atom_data = CFData::from_bytes(format.codec_configuration());
    let atoms = make_dictionary(&[(cf_ptr(&*atom_name), cf_ptr(&*atom_data))])?;
    let extensions = make_dictionary(&[(
        cf_ptr(unsafe { kCMFormatDescriptionExtension_SampleDescriptionExtensionAtoms }),
        cf_ptr(&*atoms),
    )])?;
    let mut description_ptr: *const CMFormatDescription = ptr::null();
    let status = unsafe {
        CMVideoFormatDescriptionCreate(
            None,
            kCMVideoCodecType_AV1,
            format.width(),
            format.height(),
            Some(&extensions),
            NonNull::from(&mut description_ptr),
        )
    };
    check_status("CMVideoFormatDescriptionCreate(AV1)", status)?;
    let description_ptr =
        NonNull::new(description_ptr.cast_mut()).ok_or(BackendError::AppleApi {
            api: "CMVideoFormatDescriptionCreate(AV1)",
            status: -1,
        })?;
    Ok(unsafe { CFRetained::from_raw(description_ptr) })
}

fn create_h264_format_description(
    format: &H264Format,
) -> Result<CFRetained<CMFormatDescription>, BackendError> {
    let mut pointers = [
        NonNull::new(format.parameter_sets.sequence().as_ptr().cast_mut())
            .expect("validated SPS is non-empty"),
        NonNull::new(format.parameter_sets.picture().as_ptr().cast_mut())
            .expect("validated PPS is non-empty"),
    ];
    let mut sizes = [
        format.parameter_sets.sequence().len(),
        format.parameter_sets.picture().len(),
    ];
    let mut description_ptr: *const CMFormatDescription = ptr::null();
    let status = unsafe {
        CMVideoFormatDescriptionCreateFromH264ParameterSets(
            None,
            pointers.len(),
            NonNull::new(pointers.as_mut_ptr()).expect("parameter set array is non-empty"),
            NonNull::new(sizes.as_mut_ptr()).expect("parameter set size array is non-empty"),
            4,
            NonNull::from(&mut description_ptr),
        )
    };
    check_status(
        "CMVideoFormatDescriptionCreateFromH264ParameterSets",
        status,
    )?;
    let description_ptr =
        NonNull::new(description_ptr.cast_mut()).ok_or(BackendError::AppleApi {
            api: "CMVideoFormatDescriptionCreateFromH264ParameterSets",
            status: -1,
        })?;
    Ok(unsafe { CFRetained::from_raw(description_ptr) })
}

fn create_h265_format_description(
    format: &H265Format,
) -> Result<CFRetained<CMFormatDescription>, BackendError> {
    let mut pointers = [
        NonNull::new(format.parameter_sets.video().as_ptr().cast_mut())
            .expect("validated VPS is non-empty"),
        NonNull::new(format.parameter_sets.sequence().as_ptr().cast_mut())
            .expect("validated SPS is non-empty"),
        NonNull::new(format.parameter_sets.picture().as_ptr().cast_mut())
            .expect("validated PPS is non-empty"),
    ];
    let mut sizes = [
        format.parameter_sets.video().len(),
        format.parameter_sets.sequence().len(),
        format.parameter_sets.picture().len(),
    ];
    let mut description_ptr: *const CMFormatDescription = ptr::null();
    let status = unsafe {
        CMVideoFormatDescriptionCreateFromHEVCParameterSets(
            None,
            pointers.len(),
            NonNull::new(pointers.as_mut_ptr()).expect("parameter set array is non-empty"),
            NonNull::new(sizes.as_mut_ptr()).expect("parameter set size array is non-empty"),
            4,
            None,
            NonNull::from(&mut description_ptr),
        )
    };
    check_status(
        "CMVideoFormatDescriptionCreateFromHEVCParameterSets",
        status,
    )?;
    let description_ptr =
        NonNull::new(description_ptr.cast_mut()).ok_or(BackendError::AppleApi {
            api: "CMVideoFormatDescriptionCreateFromHEVCParameterSets",
            status: -1,
        })?;
    Ok(unsafe { CFRetained::from_raw(description_ptr) })
}

fn make_dictionary(
    entries: &[(*const c_void, *const c_void)],
) -> Result<CFRetained<CFDictionary>, BackendError> {
    let mut keys: Vec<_> = entries.iter().map(|(key, _)| *key).collect();
    let mut values: Vec<_> = entries.iter().map(|(_, value)| *value).collect();
    unsafe {
        CFDictionary::new(
            None,
            keys.as_mut_ptr(),
            values.as_mut_ptr(),
            entries.len() as isize,
            &kCFTypeDictionaryKeyCallBacks,
            &kCFTypeDictionaryValueCallBacks,
        )
    }
    .ok_or_else(|| BackendError::Metal("failed to create CoreFoundation dictionary".into()))
}

fn cf_ptr<T>(value: &T) -> *const c_void {
    (value as *const T).cast()
}

fn check_status(api: &'static str, status: i32) -> Result<(), BackendError> {
    if status == 0 {
        Ok(())
    } else {
        Err(BackendError::AppleApi { api, status })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use objc2_core_media::{CMTime, CMTimeFlags};

    #[test]
    fn callbacks_preserve_sender_identity_and_reject_late_output() {
        let mailbox = Arc::new(LatestMailbox::new());
        let counters = Arc::new(Counters::default());
        let in_flight = Arc::new(InFlight {
            count: AtomicUsize::new(2),
            maximum: 2,
        });
        let mut context = CallbackContext {
            output: DecodedFrameOutput::EmbeddedMailbox {
                mailbox: Arc::clone(&mailbox),
                frame_available: None,
            },
            counters: Arc::clone(&counters),
            failures: Arc::new(FailureReporter::default()),
            in_flight: Arc::clone(&in_flight),
            color_space: VideoColorSpace::Bt709,
            transfer_function: VideoTransferFunction::Sdr,
            bit_depth: VideoBitDepth::Eight,
            frame_order: FrameOrder::default(),
        };
        let first = context.frame_order.submit();
        let second = context.frame_order.submit();
        let mut pixel_buffer = ptr::null_mut();
        let status = unsafe {
            objc2_core_video::CVPixelBufferCreate(
                None,
                16,
                16,
                kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange,
                None,
                NonNull::from(&mut pixel_buffer),
            )
        };
        assert_eq!(status, 0);
        let image = unsafe { CFRetained::from_raw(NonNull::new(pixel_buffer).unwrap()) };
        for (order, frame_index, timestamp) in
            [(second, Some(0), 0), (first, Some(u32::MAX), 90_000)]
        {
            let submission = Box::into_raw(Box::new(SubmittedFrame { order, frame_index }));
            unsafe {
                decompression_callback(
                    (&mut context as *mut CallbackContext).cast(),
                    submission.cast(),
                    0,
                    VTDecodeInfoFlags::empty(),
                    CFRetained::as_ptr(&image).as_ptr(),
                    CMTime::new(timestamp, 90_000),
                    CMTime::new(750, 90_000),
                );
            }
        }
        let frame = mailbox.take().unwrap();
        assert_eq!(frame.frame_index, Some(0));
        assert_eq!(frame.timestamp_100ns, 0);
        assert_eq!(in_flight.count.load(Ordering::Acquire), 0);
        assert_eq!(counters.video_decoded.load(Ordering::Relaxed), 2);
        assert_eq!(counters.video_frames_dropped.load(Ordering::Relaxed), 1);
    }

    #[test]
    fn main10_probe_formats_create_core_media_descriptions() {
        for format in [
            crate::format::h265_main10_probe_format(),
            crate::format::av1_main10_probe_format(),
        ] {
            create_format_description(&format).expect("Main10 probe format description");
        }
    }

    #[test]
    fn negotiated_hdr_metadata_survives_missing_pixel_buffer_attachments() {
        for (transfer, expected) in [
            (VideoTransferFunction::Pq, MetalTextureColorSpace::Pq2020),
            (VideoTransferFunction::Hlg, MetalTextureColorSpace::Hlg2020),
        ] {
            assert_eq!(
                decoded_color_space(None, None, None, VideoColorSpace::Bt2020, transfer).unwrap(),
                (VideoColorSpace::Bt2020, expected),
            );
        }
    }

    #[test]
    fn pixel_buffer_attachments_override_negotiated_color_metadata() {
        let matrix = unsafe { kCVImageBufferYCbCrMatrix_ITU_R_2020 };
        let transfer = unsafe { kCVImageBufferTransferFunction_ITU_R_2100_HLG };
        let primaries = unsafe { kCVImageBufferColorPrimaries_ITU_R_2020 };
        assert_eq!(
            decoded_color_space(
                Some(matrix.as_ref()),
                Some(transfer.as_ref()),
                Some(primaries.as_ref()),
                VideoColorSpace::Bt709,
                VideoTransferFunction::Sdr,
            )
            .unwrap(),
            (VideoColorSpace::Bt2020, MetalTextureColorSpace::Hlg2020),
        );
        let matrix = unsafe { kCVImageBufferYCbCrMatrix_ITU_R_601_4 };
        assert_eq!(
            decoded_color_space(
                Some(matrix.as_ref()),
                None,
                None,
                VideoColorSpace::Bt709,
                VideoTransferFunction::Sdr
            )
            .unwrap(),
            (VideoColorSpace::Bt601, MetalTextureColorSpace::Sdr709),
        );
    }

    #[test]
    fn invalid_or_unrepresentable_color_metadata_is_rejected() {
        let invalid = CFData::from_bytes(&[1]);
        let unknown = CFString::from_static_str("unknown matrix");
        for matrix in [invalid.as_ref() as &CFType, unknown.as_ref() as &CFType] {
            assert!(
                decoded_color_space(
                    Some(matrix),
                    None,
                    None,
                    VideoColorSpace::Bt709,
                    VideoTransferFunction::Sdr
                )
                .is_err()
            );
        }
        let transfer = unsafe { kCVImageBufferTransferFunction_SMPTE_ST_2084_PQ };
        let primaries = unsafe { kCVImageBufferColorPrimaries_ITU_R_709_2 };
        assert!(
            decoded_color_space(
                None,
                Some(transfer.as_ref()),
                Some(primaries.as_ref()),
                VideoColorSpace::Bt2020,
                VideoTransferFunction::Pq
            )
            .is_err()
        );
    }

    #[test]
    fn converts_120_hz_core_media_duration_to_seconds() {
        let duration = CMTime {
            value: 750,
            timescale: 90_000,
            flags: CMTimeFlags(1),
            epoch: 0,
        };
        assert!((frame_duration_seconds(duration) - 1.0 / 120.0).abs() < f64::EPSILON);
    }

    #[test]
    fn converts_core_media_time_to_cross_platform_100ns_units() {
        let time = CMTime {
            value: 90_000,
            timescale: 90_000,
            flags: CMTimeFlags(1),
            epoch: 0,
        };
        assert_eq!(time_to_100ns(time), 10_000_000);
    }

    #[test]
    #[ignore = "requires a Mac with VideoToolbox hardware decode and a Metal device"]
    fn hardware_decode_to_embedded_metal_survives_surface_retirement() {
        use super::*;
        use crate::{AdoptedMetalContext, EmbeddedFrameProducer, H264ParameterSets};
        use objc2::rc::Retained;
        use objc2_metal::{
            MTLCommandBuffer, MTLCommandBufferStatus, MTLCommandQueue,
            MTLCreateSystemDefaultDevice, MTLDevice,
        };

        let sps = [
            0x67, 0x42, 0xc0, 0x0a, 0xd9, 0x04, 0x26, 0xc0, 0x44, 0x00, 0x00, 0x03, 0x00, 0x04,
            0x00, 0x00, 0x03, 0x01, 0xe2, 0x3c, 0x48, 0x99, 0x20,
        ];
        let pps = [0x68, 0xcb, 0x83, 0xcb, 0x20];
        let idr = [
            0x65, 0x88, 0x84, 0x04, 0xbc, 0x98, 0xa0, 0x00, 0x38, 0xa3, 0x27, 0x27, 0x27, 0x5d,
            0x75, 0xd7, 0x5d, 0x75, 0xd7, 0x5d, 0x75, 0xd7, 0x80,
        ];
        let format = H264Format::new(
            H264ParameterSets::new(sps, pps).unwrap(),
            VideoColorSpace::Bt709,
        )
        .into();
        let device = MTLCreateSystemDefaultDevice().expect("Metal device");
        let queue = device.newCommandQueue().expect("Metal command queue");
        let mailbox = Arc::new(LatestMailbox::new());
        let counters = Arc::new(Counters::default());
        let failures = Arc::new(FailureReporter::default());
        let producer = EmbeddedFrameProducer::new(
            Arc::clone(&mailbox),
            Arc::clone(&counters),
            Arc::clone(&failures),
        );
        let decoder = VideoDecoder::new(
            &format,
            DecodedFrameOutput::EmbeddedMailbox {
                mailbox,
                frame_available: None,
            },
            Arc::clone(&counters),
            Arc::clone(&failures),
            3,
        )
        .expect("hardware VideoToolbox session");
        let mut sample = (idr.len() as u32).to_be_bytes().to_vec();
        sample.extend_from_slice(&idr);
        for _ in 0..3 {
            assert!(
                decoder
                    .submit(&sample, FrameTiming::from_90khz(90_000, 1500))
                    .unwrap()
            );
            assert_eq!(
                unsafe {
                    decoder
                        .session
                        .as_ref()
                        .unwrap()
                        .wait_for_asynchronous_frames()
                },
                0
            );
            let frame = producer.acquire_latest().expect("decoded hardware frame");
            assert_eq!((frame.width(), frame.height()), (64, 64));
            assert_eq!(frame.presentation_time_ns(), 1_000_000_000);
            let command = queue.commandBuffer().expect("Metal command buffer");
            let recorded = unsafe {
                frame.record(
                    AdoptedMetalContext {
                        device: Retained::as_ptr(&device).cast_mut().cast(),
                        command_buffer: Retained::as_ptr(&command).cast_mut().cast(),
                    },
                    0,
                )
            }
            .expect("zero-copy IOSurface import and Metal conversion");
            assert!(!recorded.texture.is_null());
            assert_eq!((recorded.width, recorded.height), (64, 64));
            producer.release_graphics_resources();
            drop(frame);
            command.commit();
            command.waitUntilCompleted();
            assert_eq!(command.status(), MTLCommandBufferStatus::Completed);
        }
        assert_eq!(counters.snapshot().video_metal_completed, 3);
        assert_eq!(counters.snapshot().video_present_errors, 0);
        assert!(failures.fatal_failure().is_none());
    }
}
