//! HDR signaling for GFN WebRTC session
//!
//! Handles SDP (Session Description Protocol) modifications to negotiate HDR streaming

use anyhow::{Result, Context};
use log::{info, warn, debug};

/// HDR streaming preferences
#[derive(Debug, Clone)]
pub struct HdrStreamingConfig {
    /// Request HDR streaming
    pub enable_hdr: bool,

    /// Preferred video codec
    pub codec: HdrCodec,

    /// Maximum bitrate in Mbps
    pub max_bitrate_mbps: u32,

    /// Display capabilities
    pub max_luminance: f32,
    pub min_luminance: f32,
    pub max_frame_avg_luminance: f32,

    /// Color space
    pub color_space: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HdrCodec {
    /// H.264 (baseline, SDR only)
    H264,

    /// H.265 Main10 profile (10-bit, HDR10)
    H265Main10,

    /// AV1 (10-bit, HDR10+)
    Av1,
}

impl HdrCodec {
    pub fn mime_type(&self) -> &'static str {
        match self {
            HdrCodec::H264 => "video/H264",
            HdrCodec::H265Main10 => "video/H265",
            HdrCodec::Av1 => "video/AV1",
        }
    }

    pub fn sdp_name(&self) -> &'static str {
        match self {
            HdrCodec::H264 => "H264",
            HdrCodec::H265Main10 => "H265",
            HdrCodec::Av1 => "AV1",
        }
    }

    pub fn profile_level_id(&self) -> Option<&'static str> {
        match self {
            HdrCodec::H264 => Some("42e01f"), // Baseline profile, level 3.1
            HdrCodec::H265Main10 => Some("1"), // Main10 profile
            HdrCodec::Av1 => None,
        }
    }
}

impl Default for HdrStreamingConfig {
    fn default() -> Self {
        Self {
            enable_hdr: false,
            codec: HdrCodec::H264,
            max_bitrate_mbps: 50,
            max_luminance: 80.0,
            min_luminance: 0.0,
            max_frame_avg_luminance: 80.0,
            color_space: "srgb".to_string(),
        }
    }
}

/// Modify SDP offer to request HDR streaming
pub fn modify_sdp_for_hdr(sdp: &str, config: &HdrStreamingConfig) -> Result<String> {
    if !config.enable_hdr {
        debug!("HDR not enabled, returning original SDP");
        return Ok(sdp.to_string());
    }

    info!("Modifying SDP to request HDR streaming");
    info!("Codec: {:?}", config.codec);
    info!("Max luminance: {} nits", config.max_luminance);

    let mut modified_sdp = sdp.to_string();

    // Add HDR codec preferences to video media section
    modified_sdp = add_hdr_codec_preference(&modified_sdp, config)?;

    // Add HDR metadata attributes
    modified_sdp = add_hdr_attributes(&modified_sdp, config)?;

    // Add bitrate constraints
    modified_sdp = add_bitrate_constraints(&modified_sdp, config)?;

    debug!("Modified SDP:\n{}", modified_sdp);

    Ok(modified_sdp)
}

/// Add HDR codec to SDP codec list with high priority
fn add_hdr_codec_preference(sdp: &str, config: &HdrStreamingConfig) -> Result<String> {
    let mut lines: Vec<String> = sdp.lines().map(|s| s.to_string()).collect();
    let mut modified = false;

    for i in 0..lines.len() {
        let line = &lines[i];

        // Find video media line
        if line.starts_with("m=video") {
            // Parse existing codec payload types
            let parts: Vec<&str> = line.split_whitespace().collect();
            if parts.len() < 4 {
                continue;
            }

            // Example: m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99
            let mut codecs: Vec<String> = parts[3..].iter().map(|s| s.to_string()).collect();

            // Add HDR codec as first preference (payload type 120 for H.265, 121 for AV1)
            let hdr_payload = match config.codec {
                HdrCodec::H264 => continue, // H.264 already in list
                HdrCodec::H265Main10 => "120",
                HdrCodec::Av1 => "121",
            };

            if !codecs.contains(&hdr_payload.to_string()) {
                codecs.insert(0, hdr_payload.to_string());
            }

            // Rebuild m= line
            let new_line = format!("{} {} {} {}",
                parts[0], // m=video
                parts[1], // port
                parts[2], // proto
                codecs.join(" ")
            );

            lines[i] = new_line;

            // Add rtpmap and fmtp lines for HDR codec
            let rtpmap = match config.codec {
                HdrCodec::H264 => continue,
                HdrCodec::H265Main10 => format!("a=rtpmap:{} {}/90000", hdr_payload, config.codec.sdp_name()),
                HdrCodec::Av1 => format!("a=rtpmap:{} {}/90000", hdr_payload, config.codec.sdp_name()),
            };

            let fmtp = match config.codec {
                HdrCodec::H264 => continue,
                HdrCodec::H265Main10 => {
                    format!("a=fmtp:{} profile-id={}; level-id=153; tier-flag=0",
                        hdr_payload,
                        config.codec.profile_level_id().unwrap_or("1")
                    )
                },
                HdrCodec::Av1 => {
                    format!("a=fmtp:{} profile=0; level-idx=8; tier=0", hdr_payload)
                },
            };

            // Insert after m= line
            lines.insert(i + 1, rtpmap);
            lines.insert(i + 2, fmtp);

            modified = true;
            break;
        }
    }

    if !modified {
        warn!("Could not find video media section in SDP");
    }

    Ok(lines.join("\r\n"))
}

/// Add HDR metadata attributes to SDP
fn add_hdr_attributes(sdp: &str, config: &HdrStreamingConfig) -> Result<String> {
    let mut lines: Vec<String> = sdp.lines().map(|s| s.to_string()).collect();
    let mut video_section_idx = None;

    // Find video media section
    for (i, line) in lines.iter().enumerate() {
        if line.starts_with("m=video") {
            video_section_idx = Some(i);
            break;
        }
    }

    if let Some(idx) = video_section_idx {
        // Find insertion point (after rtpmap/fmtp lines, before next m= line)
        let mut insert_idx = idx + 1;
        while insert_idx < lines.len() {
            if lines[insert_idx].starts_with("m=") {
                break;
            }
            if !lines[insert_idx].starts_with("a=rtpmap") &&
               !lines[insert_idx].starts_with("a=fmtp") {
                break;
            }
            insert_idx += 1;
        }

        // Add HDR metadata attributes
        let hdr_attrs = vec![
            // Signal HDR capability
            format!("a=content:hdr"),

            // Mastering display metadata (SMPTE ST 2086)
            format!("a=smpte2086:max-luminance={}; min-luminance={}",
                (config.max_luminance * 10000.0) as u32,  // Convert to 0.0001 nits units
                (config.min_luminance * 10000.0) as u32
            ),

            // Content light level (SMPTE ST 2094-40)
            format!("a=max-cll:{}; max-fall={}",
                config.max_luminance as u32,
                config.max_frame_avg_luminance as u32
            ),

            // Color space
            format!("a=colorspace:{}", if config.enable_hdr { "rec2020" } else { "rec709" }),

            // Transfer characteristics (PQ for HDR)
            format!("a=transfer:{}", if config.enable_hdr { "smpte2084" } else { "bt709" }),
        ];

        // Insert attributes
        for (offset, attr) in hdr_attrs.iter().enumerate() {
            lines.insert(insert_idx + offset, attr.clone());
        }

        info!("Added {} HDR attributes to SDP", hdr_attrs.len());
    } else {
        warn!("Could not find video section to add HDR attributes");
    }

    Ok(lines.join("\r\n"))
}

/// Add bitrate constraints for HDR streaming
fn add_bitrate_constraints(sdp: &str, config: &HdrStreamingConfig) -> Result<String> {
    let mut lines: Vec<String> = sdp.lines().map(|s| s.to_string()).collect();

    for i in 0..lines.len() {
        if lines[i].starts_with("m=video") {
            // Find existing b= line or add new one
            let mut has_bandwidth = false;
            for j in (i+1)..lines.len() {
                if lines[j].starts_with("m=") {
                    break;
                }
                if lines[j].starts_with("b=AS:") || lines[j].starts_with("b=TIAS:") {
                    // Replace existing bandwidth
                    lines[j] = format!("b=AS:{}", config.max_bitrate_mbps * 1000);
                    has_bandwidth = true;
                    break;
                }
            }

            if !has_bandwidth {
                // Add bandwidth constraint after m= line
                lines.insert(i + 1, format!("b=AS:{}", config.max_bitrate_mbps * 1000));
            }

            info!("Set video bitrate to {} Mbps", config.max_bitrate_mbps);
            break;
        }
    }

    Ok(lines.join("\r\n"))
}

/// Parse SDP answer to check if HDR was accepted
pub fn check_hdr_negotiation(sdp_answer: &str) -> Result<HdrNegotiationResult> {
    let mut result = HdrNegotiationResult {
        hdr_accepted: false,
        codec: None,
        max_luminance: None,
        color_space: None,
    };

    for line in sdp_answer.lines() {
        // Check for HDR codec in answer
        if line.contains("H265") || line.contains("HEVC") {
            result.codec = Some(HdrCodec::H265Main10);
            result.hdr_accepted = true;
        } else if line.contains("AV1") || line.contains("av1") {
            result.codec = Some(HdrCodec::Av1);
            result.hdr_accepted = true;
        }

        // Check for HDR attributes
        if line.starts_with("a=content:hdr") {
            result.hdr_accepted = true;
        }

        // Parse mastering display metadata
        if line.starts_with("a=smpte2086:") {
            if let Some(max_lum_str) = line.split("max-luminance=").nth(1) {
                if let Some(value) = max_lum_str.split(';').next() {
                    if let Ok(val) = value.parse::<u32>() {
                        result.max_luminance = Some(val as f32 / 10000.0);
                    }
                }
            }
        }

        // Parse color space
        if line.starts_with("a=colorspace:") {
            result.color_space = Some(line.split(':').nth(1).unwrap_or("unknown").to_string());
        }
    }

    if result.hdr_accepted {
        info!("HDR negotiation successful!");
        info!("  Codec: {:?}", result.codec);
        info!("  Max luminance: {:?} nits", result.max_luminance);
        info!("  Color space: {:?}", result.color_space);
    } else {
        info!("HDR not negotiated, falling back to SDR");
    }

    Ok(result)
}

#[derive(Debug, Clone)]
pub struct HdrNegotiationResult {
    pub hdr_accepted: bool,
    pub codec: Option<HdrCodec>,
    pub max_luminance: Option<f32>,
    pub color_space: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sdp_modification() {
        let original_sdp = "v=0\r\n\
            o=- 0 0 IN IP4 127.0.0.1\r\n\
            s=-\r\n\
            t=0 0\r\n\
            m=video 9 UDP/TLS/RTP/SAVPF 96 97\r\n\
            a=rtpmap:96 H264/90000\r\n\
            a=rtpmap:97 VP8/90000\r\n";

        let config = HdrStreamingConfig {
            enable_hdr: true,
            codec: HdrCodec::H265Main10,
            max_bitrate_mbps: 50,
            max_luminance: 1000.0,
            min_luminance: 0.0001,
            max_frame_avg_luminance: 400.0,
            color_space: "rec2020".to_string(),
        };

        let result = modify_sdp_for_hdr(original_sdp, &config);
        assert!(result.is_ok());

        let modified = result.unwrap();
        println!("Modified SDP:\n{}", modified);

        assert!(modified.contains("H265"));
        assert!(modified.contains("a=content:hdr"));
        assert!(modified.contains("smpte2086"));
    }
}
