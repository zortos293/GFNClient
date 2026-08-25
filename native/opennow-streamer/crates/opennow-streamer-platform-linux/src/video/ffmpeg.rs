use std::ptr;
use std::sync::{Arc, OnceLock};

use ffmpeg::codec;
use ffmpeg::ffi;
use ffmpeg::format::Pixel;
use ffmpeg::frame;
use ffmpeg::software::scaling::{context::Context as Scaler, flag::Flags as ScaleFlags};
use ffmpeg_next as ffmpeg;

use crate::{
    ChromaLocation, ColorMatrix, ColorRange, DecodedVideoFrame, EncodedVideoFrame, Error,
    FramePlane, PixelFormat, Result, StreamFormat, Subsystem, VideoCodec,
};

use super::VideoDecoder;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum FfmpegMode {
    Vulkan,
    Cuda,
    Software,
}

impl FfmpegMode {
    pub(crate) const fn label(self) -> &'static str {
        match self {
            Self::Vulkan => "FFmpeg Vulkan Video",
            Self::Cuda => "FFmpeg CUDA/NVDEC",
            Self::Software => "FFmpeg software",
        }
    }

    const fn device_type(self) -> Option<ffi::AVHWDeviceType> {
        match self {
            Self::Vulkan => Some(ffi::AVHWDeviceType::AV_HWDEVICE_TYPE_VULKAN),
            Self::Cuda => Some(ffi::AVHWDeviceType::AV_HWDEVICE_TYPE_CUDA),
            Self::Software => None,
        }
    }
}

pub(crate) struct FfmpegDecoder {
    // The decoder must be dropped before `wanted_hw_format`; libavcodec may
    // consult the callback opaque pointer during decoder teardown.
    decoder: ffmpeg::decoder::Video,
    wanted_hw_format: Option<Box<ffi::AVPixelFormat>>,
    scaler: Option<Scaler>,
    codec: VideoCodec,
    mode: FfmpegMode,
    configured_format: StreamFormat,
    pending_format_change: Option<StreamFormat>,
    last_timestamp_us: u64,
}

impl FfmpegDecoder {
    pub(crate) fn open(codec: VideoCodec, format: StreamFormat, mode: FfmpegMode) -> Result<Self> {
        initialize_ffmpeg()?;
        let decoder_definition = if mode == FfmpegMode::Software {
            ffmpeg::decoder::find(codec_id(codec))
        } else {
            ffmpeg::decoder::find_by_name(native_decoder_name(codec))
        }
        .ok_or_else(|| {
            Error::unavailable(
                Subsystem::Ffmpeg,
                format!("FFmpeg was built without the {} decoder", codec.label()),
            )
        })?;
        let mut context = codec::Context::new_with_codec(decoder_definition);
        unsafe {
            let raw = context.as_mut_ptr();
            (*raw).width = format.width as i32;
            (*raw).height = format.height as i32;
            (*raw).thread_count = if mode == FfmpegMode::Software { 0 } else { 1 };
        }

        let mut wanted_hw_format = None;
        if let Some(device_type) = mode.device_type() {
            let pixel_format =
                hardware_pixel_format(decoder_definition, device_type).ok_or_else(|| {
                    Error::unavailable(
                        Subsystem::Ffmpeg,
                        format!(
                            "{} does not expose {} decode through libavcodec",
                            mode.label(),
                            codec.label()
                        ),
                    )
                })?;
            let mut device = ptr::null_mut();
            let create_result = unsafe {
                ffi::av_hwdevice_ctx_create(
                    &mut device,
                    device_type,
                    ptr::null(),
                    ptr::null_mut(),
                    0,
                )
            };
            if create_result < 0 || device.is_null() {
                return Err(ffmpeg_error(
                    format!("failed to create the {} device", mode.label()),
                    create_result,
                ));
            }
            let mut selected = Box::new(pixel_format);
            unsafe {
                let raw = context.as_mut_ptr();
                (*raw).hw_device_ctx = ffi::av_buffer_ref(device);
                (*raw).get_format = Some(select_hardware_format);
                (*raw).opaque = (&mut *selected as *mut ffi::AVPixelFormat).cast();
                ffi::av_buffer_unref(&mut device);
                if (*raw).hw_device_ctx.is_null() {
                    return Err(Error::backend(
                        Subsystem::Ffmpeg,
                        format!("failed to retain the {} device", mode.label()),
                    ));
                }
            }
            wanted_hw_format = Some(selected);
        }

        let decoder = context
            .decoder()
            .open_as(decoder_definition)
            .and_then(|opened| opened.video())
            .map_err(|error| {
                Error::backend(
                    Subsystem::Ffmpeg,
                    format!(
                        "{} {} decoder initialization failed: {error}",
                        mode.label(),
                        codec.label()
                    ),
                )
            })?;
        Ok(Self {
            decoder,
            wanted_hw_format,
            scaler: None,
            codec,
            mode,
            configured_format: format,
            pending_format_change: None,
            last_timestamp_us: 0,
        })
    }

    pub(crate) fn probe(
        codec: VideoCodec,
        mode: FfmpegMode,
    ) -> std::result::Result<String, String> {
        let format = StreamFormat::video_default(1920, 1080).map_err(|error| error.to_string())?;
        let decoder = Self::open(codec, format, mode).map_err(|error| error.to_string())?;
        Ok(format!(
            "{} {} via libavcodec {}",
            decoder.mode.label(),
            decoder.codec.label(),
            ffmpeg::codec::version()
        ))
    }

    fn drain(&mut self, draining: bool) -> Result<Vec<DecodedVideoFrame>> {
        let mut frames = Vec::new();
        loop {
            let mut decoded = frame::Video::empty();
            match self.decoder.receive_frame(&mut decoded) {
                Ok(()) => frames.push(self.convert_frame(&decoded)?),
                Err(ffmpeg::Error::Other { errno }) if errno == ffmpeg::error::EAGAIN => break,
                Err(ffmpeg::Error::Eof) if draining => break,
                Err(error) => {
                    return Err(Error::backend(
                        Subsystem::Ffmpeg,
                        format!(
                            "{} {} frame receive failed: {error}",
                            self.mode.label(),
                            self.codec.label()
                        ),
                    ));
                }
            }
        }
        Ok(frames)
    }

    fn convert_frame(&mut self, decoded: &frame::Video) -> Result<DecodedVideoFrame> {
        let software_frame;
        let source = if self
            .wanted_hw_format
            .as_deref()
            .is_some_and(|format| Pixel::from(*format) == decoded.format())
        {
            let mut transferred = frame::Video::empty();
            let transfer_result = unsafe {
                ffi::av_hwframe_transfer_data(transferred.as_mut_ptr(), decoded.as_ptr(), 0)
            };
            if transfer_result < 0 {
                return Err(ffmpeg_error(
                    format!("{} frame download failed", self.mode.label()),
                    transfer_result,
                ));
            }
            unsafe {
                ffi::av_frame_copy_props(transferred.as_mut_ptr(), decoded.as_ptr());
            }
            software_frame = transferred;
            &software_frame
        } else {
            decoded
        };

        let width = source.width().max(1);
        let height = source.height().max(1);
        // NVDEC and Vulkan Video normally download NV12 already. Avoid a full
        // swscale pass in that hot path; conversion is only needed for formats
        // such as software-decoded YUV420P or 10-bit hardware output.
        let converted;
        let nv12 = if source.format() == Pixel::NV12 {
            source
        } else {
            let scaler_changed = self.scaler.as_ref().is_none_or(|scaler| {
                scaler.input().format != source.format()
                    || scaler.input().width != width
                    || scaler.input().height != height
            });
            if scaler_changed {
                self.scaler = Some(
                    Scaler::get(
                        source.format(),
                        width,
                        height,
                        Pixel::NV12,
                        width,
                        height,
                        ScaleFlags::BILINEAR,
                    )
                    .map_err(|error| {
                        Error::backend(
                            Subsystem::Ffmpeg,
                            format!("NV12 conversion setup failed: {error}"),
                        )
                    })?,
                );
            }
            let mut frame = frame::Video::empty();
            self.scaler
                .as_mut()
                .expect("scaler was initialized")
                .run(source, &mut frame)
                .map_err(|error| {
                    Error::backend(
                        Subsystem::Ffmpeg,
                        format!("NV12 conversion failed: {error}"),
                    )
                })?;
            converted = frame;
            &converted
        };

        let output_format = StreamFormat {
            width,
            height,
            pixel_format: PixelFormat::Nv12,
            color_range: map_color_range(source),
            color_matrix: map_color_matrix(source),
            chroma_location: map_chroma_location(source),
        };
        output_format.validate()?;
        if output_format != self.configured_format {
            self.configured_format = output_format;
            self.pending_format_change = Some(output_format);
        }
        let timestamp_us = decoded
            .pts()
            .and_then(|timestamp| u64::try_from(timestamp).ok())
            .unwrap_or(self.last_timestamp_us);
        Ok(DecodedVideoFrame {
            format: output_format,
            planes: vec![
                FramePlane {
                    data: Arc::from(nv12.data(0).to_vec()),
                    stride: nv12.stride(0),
                    rows: height as usize,
                },
                FramePlane {
                    data: Arc::from(nv12.data(1).to_vec()),
                    stride: nv12.stride(1),
                    rows: height as usize / 2,
                },
            ],
            timestamp_us,
        })
    }
}

impl VideoDecoder for FfmpegDecoder {
    fn decode(&mut self, frame: &EncodedVideoFrame) -> Result<Vec<DecodedVideoFrame>> {
        self.last_timestamp_us = frame.timestamp_us;
        let mut packet = ffmpeg::Packet::copy(&frame.data);
        packet.set_pts(i64::try_from(frame.timestamp_us).ok());
        packet.set_dts(i64::try_from(frame.timestamp_us).ok());
        if frame.keyframe {
            packet.set_flags(ffmpeg::packet::Flags::KEY);
        }
        self.decoder.send_packet(&packet).map_err(|error| {
            Error::backend(
                Subsystem::Ffmpeg,
                format!(
                    "{} {} packet submission failed: {error}",
                    self.mode.label(),
                    self.codec.label()
                ),
            )
        })?;
        self.drain(false)
    }

    fn flush(&mut self) -> Result<Vec<DecodedVideoFrame>> {
        match self.decoder.send_eof() {
            Ok(()) | Err(ffmpeg::Error::Eof) => self.drain(true),
            Err(error) => Err(Error::backend(
                Subsystem::Ffmpeg,
                format!("{} decoder flush failed: {error}", self.mode.label()),
            )),
        }
    }

    fn take_format_change(&mut self) -> Option<StreamFormat> {
        self.pending_format_change.take()
    }
}

fn initialize_ffmpeg() -> Result<()> {
    static INITIALIZED: OnceLock<std::result::Result<(), String>> = OnceLock::new();
    INITIALIZED
        .get_or_init(|| ffmpeg::init().map_err(|error| error.to_string()))
        .clone()
        .map_err(|reason| Error::unavailable(Subsystem::Ffmpeg, reason))
}

fn codec_id(codec: VideoCodec) -> codec::Id {
    match codec {
        VideoCodec::H264 => codec::Id::H264,
        VideoCodec::H265 => codec::Id::HEVC,
        VideoCodec::Av1 => codec::Id::AV1,
    }
}

fn native_decoder_name(codec: VideoCodec) -> &'static str {
    match codec {
        VideoCodec::H264 => "h264",
        VideoCodec::H265 => "hevc",
        VideoCodec::Av1 => "av1",
    }
}

fn hardware_pixel_format(
    codec: ffmpeg::Codec,
    device_type: ffi::AVHWDeviceType,
) -> Option<ffi::AVPixelFormat> {
    let mut index = 0;
    loop {
        let config = unsafe { ffi::avcodec_get_hw_config(codec.as_ptr(), index) };
        if config.is_null() {
            return None;
        }
        let matches = unsafe {
            (*config).device_type == device_type
                && ((*config).methods as u32 & ffi::AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX as u32)
                    != 0
        };
        if matches {
            return Some(unsafe { (*config).pix_fmt });
        }
        index += 1;
    }
}

unsafe extern "C" fn select_hardware_format(
    context: *mut ffi::AVCodecContext,
    formats: *const ffi::AVPixelFormat,
) -> ffi::AVPixelFormat {
    if context.is_null() || formats.is_null() || unsafe { (*context).opaque.is_null() } {
        return ffi::AVPixelFormat::AV_PIX_FMT_NONE;
    }
    let wanted = unsafe { *((*context).opaque as *const ffi::AVPixelFormat) };
    let mut current = formats;
    while unsafe { *current } != ffi::AVPixelFormat::AV_PIX_FMT_NONE {
        if unsafe { *current } == wanted {
            return wanted;
        }
        current = unsafe { current.add(1) };
    }
    ffi::AVPixelFormat::AV_PIX_FMT_NONE
}

fn ffmpeg_error(operation: String, code: i32) -> Error {
    Error::backend(
        Subsystem::Ffmpeg,
        format!("{operation}: {}", ffmpeg::Error::from(code)),
    )
}

fn map_color_range(frame: &frame::Video) -> ColorRange {
    match frame.color_range() {
        ffmpeg::color::Range::JPEG => ColorRange::Full,
        _ => ColorRange::Limited,
    }
}

fn map_color_matrix(frame: &frame::Video) -> ColorMatrix {
    match frame.color_space() {
        ffmpeg::color::Space::BT2020NCL | ffmpeg::color::Space::BT2020CL => ColorMatrix::Bt2020,
        ffmpeg::color::Space::BT470BG | ffmpeg::color::Space::SMPTE170M => ColorMatrix::Bt601,
        _ => ColorMatrix::Bt709,
    }
}

fn map_chroma_location(frame: &frame::Video) -> ChromaLocation {
    match frame.chroma_location() {
        ffmpeg::chroma::Location::Center => ChromaLocation::Center,
        _ => ChromaLocation::Left,
    }
}

#[cfg(test)]
mod tests {
    use std::process::Command;

    use super::*;

    #[test]
    fn all_required_software_decoders_are_linked() {
        initialize_ffmpeg().unwrap();
        for codec in [VideoCodec::H264, VideoCodec::H265, VideoCodec::Av1] {
            assert!(ffmpeg::decoder::find(codec_id(codec)).is_some());
        }
    }

    #[test]
    #[ignore = "requires the FFmpeg CLI and local Vulkan/CUDA video hardware"]
    fn local_hardware_decodes_all_required_codecs() {
        for codec in [VideoCodec::H264, VideoCodec::H265, VideoCodec::Av1] {
            let (encoder, muxer, codec_options): (&str, &str, &[&str]) = match codec {
                VideoCodec::H264 => ("libx264", "h264", &["-tune", "zerolatency"]),
                VideoCodec::H265 => (
                    "libx265",
                    "hevc",
                    &["-x265-params", "log-level=error:pools=1"],
                ),
                VideoCodec::Av1 => ("libsvtav1", "obu", &["-preset", "13"]),
            };
            let mut command = Command::new("ffmpeg");
            command.args([
                "-hide_banner",
                "-loglevel",
                "error",
                "-f",
                "lavfi",
                "-i",
                "color=c=blue:size=256x144:rate=60",
                "-frames:v",
                "1",
                "-c:v",
                encoder,
            ]);
            command.args(codec_options);
            let encoded = command
                .args(["-f", muxer, "pipe:1"])
                .output()
                .expect("FFmpeg CLI must start");
            assert!(
                encoded.status.success(),
                "sample encode failed for {}: {}",
                codec.label(),
                String::from_utf8_lossy(&encoded.stderr)
            );
            assert!(!encoded.stdout.is_empty());

            for mode in [FfmpegMode::Vulkan, FfmpegMode::Cuda] {
                let frame = (|| {
                    let format = StreamFormat::video_default(256, 144)?;
                    let mut decoder = FfmpegDecoder::open(codec, format, mode)?;
                    let packet = EncodedVideoFrame::new(encoded.stdout.clone(), 1, true)?;
                    let mut frames = decoder.decode(&packet)?;
                    frames.extend(decoder.flush()?);
                    if frames.len() != 1 {
                        return Err(Error::backend(
                            Subsystem::Ffmpeg,
                            format!("expected one frame, received {}", frames.len()),
                        ));
                    }
                    Ok(frames.remove(0))
                })()
                .unwrap_or_else(|error: Error| {
                    panic!("{} failed via {}: {error}", codec.label(), mode.label())
                });
                eprintln!("{} decoded via {}", codec.label(), mode.label());
                frame.validate().unwrap();
                assert_eq!(
                    (frame.format.width, frame.format.height),
                    (256, 144),
                    "{} dimensions changed via {}",
                    codec.label(),
                    mode.label()
                );
            }
        }
    }
}
