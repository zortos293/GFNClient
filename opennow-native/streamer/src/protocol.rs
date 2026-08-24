use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Deserialize, PartialEq, Eq)]
#[serde(tag = "type", rename_all = "kebab-case")]
pub enum Command {
    Hello,
    StartDemo {
        #[serde(default = "default_quality")]
        quality: String,
    },
    Stop,
    SetQuality {
        quality: String,
    },
    Ping,
    Shutdown,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Quality {
    pub id: &'static str,
    pub width: i32,
    pub height: i32,
    pub fps: i32,
    pub bitrate_kbps: u32,
}

impl Quality {
    pub fn parse(value: &str) -> Self {
        match value.trim().to_ascii_lowercase().as_str() {
            "720p60" => Self {
                id: "720p60",
                width: 1280,
                height: 720,
                fps: 60,
                bitrate_kbps: 8_000,
            },
            "1080p60" => Self {
                id: "1080p60",
                width: 1920,
                height: 1080,
                fps: 60,
                bitrate_kbps: 18_000,
            },
            "4k60" => Self {
                id: "4k60",
                width: 3840,
                height: 2160,
                fps: 60,
                bitrate_kbps: 35_000,
            },
            _ => Self {
                id: "1080p120",
                width: 1920,
                height: 1080,
                fps: 120,
                bitrate_kbps: 28_000,
            },
        }
    }

    pub fn display_resolution(self) -> String {
        format!("{} × {}", self.width, self.height)
    }
}

#[derive(Debug, Serialize)]
#[serde(tag = "type", rename_all = "kebab-case")]
pub enum Event<'a> {
    Hello {
        protocol: u32,
        runtime: &'a str,
        gstreamer: String,
        webrtc: bool,
        vp8: bool,
    },
    State {
        phase: &'a str,
        message: &'a str,
    },
    Stats {
        codec: &'a str,
        resolution: String,
        fps: u64,
        #[serde(rename = "bitrateKbps")]
        bitrate_kbps: u64,
        #[serde(rename = "latencyMs")]
        latency_ms: u32,
        #[serde(rename = "packetLoss")]
        packet_loss: f64,
        #[serde(rename = "framesDecoded")]
        frames_decoded: u64,
    },
    Pong {
        monotonic_ms: u128,
    },
    Error {
        code: &'a str,
        message: String,
    },
}

fn default_quality() -> String {
    "720p60".to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_commands_and_defaults_quality() {
        let command: Command = serde_json::from_str(r#"{"type":"start-demo"}"#).unwrap();
        assert_eq!(
            command,
            Command::StartDemo {
                quality: "720p60".to_owned()
            }
        );
    }

    #[test]
    fn normalizes_unknown_quality_to_performance() {
        assert_eq!(Quality::parse("unexpected").id, "1080p120");
        assert_eq!(Quality::parse("4K60").width, 3840);
    }
}
