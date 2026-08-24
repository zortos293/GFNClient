mod protocol;
mod session;

use std::io::{self, BufRead, Write};
use std::sync::mpsc;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use gstreamer as gst;
use protocol::{Command, Event, Quality};
use session::Session;

#[derive(Clone)]
pub struct Emitter {
    output: Arc<Mutex<io::Stdout>>,
}

impl Emitter {
    fn new() -> Self {
        Self {
            output: Arc::new(Mutex::new(io::stdout())),
        }
    }

    pub fn emit(&self, event: &Event<'_>) {
        let Ok(line) = serde_json::to_string(event) else {
            return;
        };
        if let Ok(mut output) = self.output.lock() {
            let _ = writeln!(output, "{line}");
            let _ = output.flush();
        }
    }
}

fn main() -> anyhow::Result<()> {
    gst::init()?;

    let emitter = Emitter::new();
    let (command_tx, command_rx) = mpsc::channel::<Command>();

    thread::spawn(move || {
        for line in io::stdin().lock().lines() {
            let Ok(line) = line else {
                break;
            };
            match serde_json::from_str::<Command>(&line) {
                Ok(command) => {
                    if command_tx.send(command).is_err() {
                        break;
                    }
                }
                Err(error) => {
                    eprintln!("Invalid command: {error}");
                }
            }
        }
    });

    let started_at = Instant::now();
    let mut session: Option<Session> = None;
    loop {
        match command_rx.recv_timeout(Duration::from_millis(50)) {
            Ok(Command::Hello) => emit_hello(&emitter),
            Ok(Command::StartDemo { quality: requested }) => {
                if let Some(active) = session.take() {
                    active.stop(&emitter);
                }
                let quality = Quality::parse(&requested);
                match Session::start(quality, emitter.clone()) {
                    Ok(next) => session = Some(next),
                    Err(error) => emitter.emit(&Event::Error {
                        code: "start-failed",
                        message: format!("Failed to start WebRTC demo: {error:#}"),
                    }),
                }
            }
            Ok(Command::Stop) => {
                if let Some(active) = session.take() {
                    active.stop(&emitter);
                } else {
                    emitter.emit(&Event::State {
                        phase: "idle",
                        message: "No active session",
                    });
                }
            }
            Ok(Command::SetQuality { quality: requested }) => {
                let quality = Quality::parse(&requested);
                if let Some(active) = session.take() {
                    active.stop(&emitter);
                    match Session::start(quality, emitter.clone()) {
                        Ok(next) => session = Some(next),
                        Err(error) => emitter.emit(&Event::Error {
                            code: "quality-restart-failed",
                            message: format!("Failed to restart WebRTC demo: {error:#}"),
                        }),
                    }
                }
            }
            Ok(Command::Ping) => emitter.emit(&Event::Pong {
                monotonic_ms: started_at.elapsed().as_millis(),
            }),
            Ok(Command::Shutdown) | Err(mpsc::RecvTimeoutError::Disconnected) => break,
            Err(mpsc::RecvTimeoutError::Timeout) => {}
        }

        if let Some(active) = session.as_mut() {
            if let Err(error) = active.poll(&emitter) {
                emitter.emit(&Event::Error {
                    code: "pipeline-error",
                    message: error.to_string(),
                });
                if let Some(active) = session.take() {
                    active.stop(&emitter);
                }
            }
        }
    }

    if let Some(active) = session.take() {
        active.stop(&emitter);
    }
    Ok(())
}

fn emit_hello(emitter: &Emitter) {
    let (major, minor, micro, nano) = gst::version();
    emitter.emit(&Event::Hello {
        protocol: 1,
        runtime: "gstreamer-webrtc",
        gstreamer: format!("{major}.{minor}.{micro}.{nano}"),
        webrtc: gst::ElementFactory::find("webrtcbin").is_some(),
        vp8: gst::ElementFactory::find("vp8enc").is_some()
            && gst::ElementFactory::find("vp8dec").is_some(),
    });
}
