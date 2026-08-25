use std::sync::Arc;

use crate::{Error, Result};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum VideoCodec {
    H264,
    H265,
    Av1,
}

impl VideoCodec {
    pub const fn label(self) -> &'static str {
        match self {
            Self::H264 => "h264",
            Self::H265 => "h265",
            Self::Av1 => "av1",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PixelFormat {
    Nv12,
    I420,
    Bgra8,
    Rgba8,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ColorRange {
    Limited,
    Full,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ColorMatrix {
    Bt601,
    Bt709,
    Bt2020,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ChromaLocation {
    Left,
    Center,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct StreamFormat {
    pub width: u32,
    pub height: u32,
    pub pixel_format: PixelFormat,
    pub color_range: ColorRange,
    pub color_matrix: ColorMatrix,
    pub chroma_location: ChromaLocation,
}

impl StreamFormat {
    pub fn video_default(width: u32, height: u32) -> Result<Self> {
        let format = Self {
            width,
            height,
            pixel_format: PixelFormat::Nv12,
            color_range: ColorRange::Limited,
            color_matrix: if height > 576 {
                ColorMatrix::Bt709
            } else {
                ColorMatrix::Bt601
            },
            chroma_location: ChromaLocation::Left,
        };
        format.validate()?;
        Ok(format)
    }

    pub fn h264_default(width: u32, height: u32) -> Result<Self> {
        Self::video_default(width, height)
    }

    pub fn validate(&self) -> Result<()> {
        if self.width == 0 || self.height == 0 {
            return Err(Error::InvalidFormat(
                "video dimensions must be non-zero".to_owned(),
            ));
        }
        if self.width > 16_384 || self.height > 16_384 {
            return Err(Error::InvalidFormat(
                "video dimensions exceed the Linux backend limit".to_owned(),
            ));
        }
        if matches!(self.pixel_format, PixelFormat::Nv12 | PixelFormat::I420)
            && (self.width % 2 != 0 || self.height % 2 != 0)
        {
            return Err(Error::InvalidFormat(
                "4:2:0 video dimensions must be even".to_owned(),
            ));
        }
        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct EncodedVideoFrame {
    pub data: Arc<[u8]>,
    pub timestamp_us: u64,
    pub keyframe: bool,
}

impl EncodedVideoFrame {
    pub fn new(data: impl Into<Arc<[u8]>>, timestamp_us: u64, keyframe: bool) -> Result<Self> {
        let data = data.into();
        let frame = Self {
            data,
            timestamp_us,
            keyframe,
        };
        frame.validate()?;
        Ok(frame)
    }

    pub fn validate(&self) -> Result<()> {
        if self.data.is_empty() {
            return Err(Error::InvalidFormat(
                "video access unit is empty".to_owned(),
            ));
        }
        if self.data.len() > 16 * 1024 * 1024 {
            return Err(Error::InvalidFormat(
                "video access unit exceeds 16 MiB".to_owned(),
            ));
        }
        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct FramePlane {
    pub data: Arc<[u8]>,
    pub stride: usize,
    pub rows: usize,
}

impl FramePlane {
    pub fn validate(&self, minimum_row_bytes: usize) -> Result<()> {
        if self.stride < minimum_row_bytes {
            return Err(Error::InvalidFormat(format!(
                "plane stride {} is less than the required {minimum_row_bytes}",
                self.stride
            )));
        }
        let required = self
            .stride
            .checked_mul(self.rows)
            .ok_or_else(|| Error::InvalidFormat("plane size overflow".to_owned()))?;
        if self.data.len() < required {
            return Err(Error::InvalidFormat(format!(
                "plane has {} bytes but requires {required}",
                self.data.len()
            )));
        }
        Ok(())
    }
}

#[derive(Debug, Clone)]
pub struct DecodedVideoFrame {
    pub format: StreamFormat,
    pub planes: Vec<FramePlane>,
    pub timestamp_us: u64,
}

impl DecodedVideoFrame {
    pub fn validate(&self) -> Result<()> {
        self.format.validate()?;
        let width = self.format.width as usize;
        let height = self.format.height as usize;
        match self.format.pixel_format {
            PixelFormat::Nv12 => {
                if self.planes.len() != 2 {
                    return Err(Error::InvalidFormat(
                        "NV12 frames require two planes".to_owned(),
                    ));
                }
                self.planes[0].validate(width)?;
                self.planes[1].validate(width)?;
                if self.planes[0].rows < height || self.planes[1].rows < height / 2 {
                    return Err(Error::InvalidFormat(
                        "NV12 plane height is too small".to_owned(),
                    ));
                }
            }
            PixelFormat::I420 => {
                if self.planes.len() != 3 {
                    return Err(Error::InvalidFormat(
                        "I420 frames require three planes".to_owned(),
                    ));
                }
                self.planes[0].validate(width)?;
                self.planes[1].validate(width / 2)?;
                self.planes[2].validate(width / 2)?;
                if self.planes[0].rows < height
                    || self.planes[1].rows < height / 2
                    || self.planes[2].rows < height / 2
                {
                    return Err(Error::InvalidFormat(
                        "I420 plane height is too small".to_owned(),
                    ));
                }
            }
            PixelFormat::Bgra8 | PixelFormat::Rgba8 => {
                if self.planes.len() != 1 {
                    return Err(Error::InvalidFormat(
                        "packed RGB frames require one plane".to_owned(),
                    ));
                }
                self.planes[0].validate(width * 4)?;
                if self.planes[0].rows < height {
                    return Err(Error::InvalidFormat(
                        "packed RGB plane height is too small".to_owned(),
                    ));
                }
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_padded_nv12_planes() {
        let frame = DecodedVideoFrame {
            format: StreamFormat::h264_default(4, 4).unwrap(),
            planes: vec![
                FramePlane {
                    data: Arc::from(vec![0_u8; 32]),
                    stride: 8,
                    rows: 4,
                },
                FramePlane {
                    data: Arc::from(vec![0_u8; 16]),
                    stride: 8,
                    rows: 2,
                },
            ],
            timestamp_us: 1,
        };
        frame.validate().unwrap();
    }

    #[test]
    fn rejects_odd_420_dimensions_and_short_planes() {
        assert!(StreamFormat::h264_default(1919, 1080).is_err());
        let frame = DecodedVideoFrame {
            format: StreamFormat::h264_default(4, 4).unwrap(),
            planes: vec![
                FramePlane {
                    data: Arc::from(vec![0_u8; 15]),
                    stride: 4,
                    rows: 4,
                },
                FramePlane {
                    data: Arc::from(vec![0_u8; 8]),
                    stride: 4,
                    rows: 2,
                },
            ],
            timestamp_us: 1,
        };
        assert!(frame.validate().is_err());
    }
}
