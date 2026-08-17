use std::marker::PhantomData;
use std::rc::Rc;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, Receiver, Sender};
use std::time::Duration;

use opennow_streamer_protocol::RenderSurface;

use crate::media::{MediaFeedback, MediaSession};
use crate::output::{ActiveOutput, OutputBuffers};

const HOST_POLL_INTERVAL: Duration = Duration::from_millis(2);
const HOST_START_TIMEOUT: Duration = Duration::from_secs(10);
const HOST_CONTROL_TIMEOUT: Duration = Duration::from_secs(2);

pub(crate) enum HostCommand {
    Start {
        reply: Sender<Result<(), String>>,
        feedback: Sender<MediaFeedback>,
    },
    Pause {
        paused: bool,
        reply: Option<Sender<Result<(), String>>>,
    },
    Surface {
        surface: RenderSurface,
        reply: Sender<Result<(), String>>,
    },
    Stop,
    Shutdown,
}

#[derive(Clone)]
pub struct MediaRuntime {
    commands: Sender<HostCommand>,
    output: Arc<OutputBuffers>,
    paused: Arc<AtomicBool>,
}

impl MediaRuntime {
    pub fn start(&self, feedback: Sender<MediaFeedback>) -> Result<MediaSession, String> {
        let (reply, response) = mpsc::channel();
        self.commands
            .send(HostCommand::Start {
                reply,
                feedback: feedback.clone(),
            })
            .map_err(|_| "native media host is no longer running".to_owned())?;
        response
            .recv_timeout(HOST_START_TIMEOUT)
            .map_err(|_| "native media host did not start on the UI thread".to_owned())??;
        match MediaSession::spawn(Arc::clone(&self.output), feedback, self.commands.clone()) {
            Ok(session) => {
                if self.paused.load(Ordering::Acquire) {
                    session.set_paused(true);
                }
                Ok(session)
            }
            Err(error) => {
                let _ = self.commands.send(HostCommand::Stop);
                Err(error)
            }
        }
    }

    pub fn update_surface(&self, surface: RenderSurface) -> Result<(), String> {
        let (reply, response) = mpsc::channel();
        self.commands
            .send(HostCommand::Surface { surface, reply })
            .map_err(|_| "native media host is no longer running".to_owned())?;
        response
            .recv_timeout(HOST_CONTROL_TIMEOUT)
            .map_err(|_| "native media host did not apply the Electron surface update".to_owned())?
    }

    pub fn set_paused(&self, paused: bool) -> Result<(), String> {
        self.paused.store(paused, Ordering::Release);
        let (reply, response) = mpsc::channel();
        self.commands
            .send(HostCommand::Pause {
                paused,
                reply: Some(reply),
            })
            .map_err(|_| "native media host is no longer running".to_owned())?;
        response
            .recv_timeout(HOST_CONTROL_TIMEOUT)
            .map_err(|_| "native media host did not apply the pause update".to_owned())?
    }

    pub fn shutdown(&self) {
        let _ = self.commands.send(HostCommand::Shutdown);
    }
}

pub struct MainThreadHost {
    commands: Receiver<HostCommand>,
    output: Arc<OutputBuffers>,
    _not_send: PhantomData<Rc<()>>,
}

impl MainThreadHost {
    pub fn run(self) {
        let mut active: Option<ActiveOutput> = None;
        let mut surface: Option<RenderSurface> = None;
        let mut paused = false;
        let mut feedback: Option<Sender<MediaFeedback>> = None;
        loop {
            match self.commands.recv_timeout(HOST_POLL_INTERVAL) {
                Ok(HostCommand::Start {
                    reply,
                    feedback: session_feedback,
                }) => {
                    if let Some(output) = active.as_mut() {
                        output.stop();
                    }
                    match ActiveOutput::initialize(Arc::clone(&self.output)) {
                        Ok(mut output) => {
                            if let Err(error) = output.start(surface.as_ref()) {
                                output.stop();
                                active = None;
                                feedback = None;
                                let _ = reply.send(Err(error));
                                continue;
                            }
                            output.set_paused(paused);
                            active = Some(output);
                            feedback = Some(session_feedback);
                            let _ = reply.send(Ok(()));
                        }
                        Err(error) => {
                            active = None;
                            feedback = None;
                            let _ = reply.send(Err(error));
                        }
                    }
                }
                Ok(HostCommand::Pause {
                    paused: new_paused,
                    reply,
                }) => {
                    if let Some(output) = active.as_mut() {
                        output.set_paused(new_paused);
                    }
                    paused = new_paused;
                    if let Some(reply) = reply {
                        let _ = reply.send(Ok(()));
                    }
                }
                Ok(HostCommand::Surface {
                    surface: new_surface,
                    reply,
                }) => {
                    let result = if let Some(output) = active.as_mut() {
                        output.update_surface(&new_surface)
                    } else {
                        Ok(())
                    };
                    if let Err(message) = &result {
                        if let Some(output) = active.as_mut() {
                            if let Some(feedback) = feedback.as_ref() {
                                let _ = feedback.send(MediaFeedback::OutputError {
                                    message: message.clone(),
                                });
                            }
                            output.stop();
                            active = None;
                            feedback = None;
                        }
                    }
                    surface = Some(new_surface);
                    let _ = reply.send(result);
                }
                Ok(HostCommand::Stop) => {
                    if let Some(output) = active.as_mut() {
                        output.stop();
                    }
                    active = None;
                    feedback = None;
                }
                Ok(HostCommand::Shutdown) | Err(mpsc::RecvTimeoutError::Disconnected) => {
                    if let Some(output) = active.as_mut() {
                        output.stop();
                    }
                    return;
                }
                Err(mpsc::RecvTimeoutError::Timeout) => {}
            }
            if let Some(output) = active.as_mut()
                && let Err(message) = output.pump()
            {
                if let Some(feedback) = feedback.as_ref() {
                    let _ = feedback.send(MediaFeedback::OutputError { message });
                }
                output.stop();
                active = None;
                feedback = None;
            }
        }
    }
}

pub fn create_runtime() -> Result<(MainThreadHost, MediaRuntime), String> {
    ensure_macos_main_thread()?;
    let (commands, receiver) = mpsc::channel();
    let output = Arc::new(OutputBuffers::new());
    let paused = Arc::new(AtomicBool::new(false));
    Ok((
        MainThreadHost {
            commands: receiver,
            output: Arc::clone(&output),
            _not_send: PhantomData,
        },
        MediaRuntime {
            commands,
            output,
            paused,
        },
    ))
}

#[cfg(target_os = "macos")]
fn ensure_macos_main_thread() -> Result<(), String> {
    if unsafe { libc::pthread_main_np() } == 1 {
        Ok(())
    } else {
        Err(
            "the macOS native media host must be created and run on the process main thread"
                .to_owned(),
        )
    }
}

#[cfg(not(target_os = "macos"))]
fn ensure_macos_main_thread() -> Result<(), String> {
    Ok(())
}
