#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

use std::io::{self, BufRead, Write};
use std::sync::mpsc;
use std::thread;

use opennow_streamer_core::Engine;
use opennow_streamer_platform::{MediaRuntime, create_runtime};
use opennow_streamer_protocol::{Command, error};
use serde_json::Value;

fn write_message(message: &Value) -> io::Result<()> {
    let mut stdout = io::stdout().lock();
    serde_json::to_writer(&mut stdout, message)?;
    writeln!(stdout)?;
    stdout.flush()
}

fn run_protocol(media_runtime: MediaRuntime) -> io::Result<()> {
    let (event_tx, event_rx) = mpsc::channel::<Value>();
    let event_writer = thread::spawn(move || {
        while let Ok(message) = event_rx.recv() {
            if write_message(&message).is_err() {
                break;
            }
        }
    });
    let mut engine = Engine::with_media_runtime(event_tx, media_runtime);

    for line in io::stdin().lock().lines() {
        let line = line?;
        if line.trim().is_empty() {
            continue;
        }
        let command: Command = match serde_json::from_str(&line) {
            Ok(command) => command,
            Err(parse_error) => {
                write_message(&error(None, "invalid-command", parse_error.to_string()))?;
                continue;
            }
        };
        let (responses, keep_running) = engine.handle(command);
        for response in responses {
            write_message(&response)?;
        }
        if !keep_running {
            break;
        }
    }

    drop(engine);
    let _ = event_writer.join();
    Ok(())
}

fn main() -> io::Result<()> {
    let (host, media_runtime) = create_runtime().map_err(io::Error::other)?;
    let shutdown_runtime = media_runtime.clone();
    let protocol = thread::Builder::new()
        .name("opennow-protocol".to_owned())
        .spawn(move || {
            let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                run_protocol(media_runtime)
            }));
            shutdown_runtime.shutdown();
            result
        })?;
    host.run();
    match protocol.join() {
        Ok(Ok(result)) => result,
        Ok(Err(payload)) => std::panic::resume_unwind(payload),
        Err(payload) => std::panic::resume_unwind(payload),
    }
}
