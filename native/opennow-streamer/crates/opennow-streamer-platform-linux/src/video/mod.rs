use std::path::PathBuf;

use crate::{DecodedVideoFrame, EncodedVideoFrame, Result, StreamFormat};

mod v4l2;
#[cfg(feature = "vaapi")]
mod vaapi;

pub(crate) use v4l2::probe_v4l2_devices;

pub(crate) trait VideoDecoder {
    fn decode(&mut self, frame: &EncodedVideoFrame) -> Result<Vec<DecodedVideoFrame>>;
    fn flush(&mut self) -> Result<Vec<DecodedVideoFrame>>;
    fn take_format_change(&mut self) -> Option<StreamFormat>;
}

pub(crate) fn open_v4l2(
    format: StreamFormat,
    device: Option<PathBuf>,
) -> Result<Box<dyn VideoDecoder>> {
    Ok(Box::new(v4l2::V4l2Decoder::open(format, device)?))
}

#[cfg(feature = "vaapi")]
pub(crate) fn open_vaapi(format: StreamFormat) -> Result<Box<dyn VideoDecoder>> {
    Ok(Box::new(vaapi::VaApiDecoder::open(format)?))
}

#[cfg(feature = "vaapi")]
pub(crate) fn probe_vaapi() -> std::result::Result<String, String> {
    vaapi::VaApiDecoder::probe()
}

#[cfg(not(feature = "vaapi"))]
pub(crate) fn probe_vaapi() -> std::result::Result<String, String> {
    Err("crate was built without the vaapi feature".to_owned())
}
