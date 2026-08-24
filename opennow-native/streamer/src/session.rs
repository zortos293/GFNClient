use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use anyhow::{anyhow, Context, Result};
use gstreamer as gst;
use gstreamer::prelude::*;
use gstreamer_webrtc as gst_webrtc;

use crate::protocol::{Event, Quality};
use crate::Emitter;

const WEBRTC_LATENCY_MS: u32 = 2;

pub struct Session {
    pipeline: gst::Pipeline,
    quality: Quality,
    frames: Arc<AtomicU64>,
    rtp_bytes: Arc<AtomicU64>,
    last_sample: Instant,
    last_frames: u64,
    last_bytes: u64,
}

impl Session {
    pub fn start(quality: Quality, emitter: Emitter) -> Result<Self> {
        emitter.emit(&Event::State {
            phase: "connecting",
            message: "Creating two local WebRTC peers",
        });

        let description = format!(
            "webrtcbin name=sender bundle-policy=max-bundle latency={latency} \
             webrtcbin name=receiver bundle-policy=max-bundle latency={latency} \
             videotestsrc is-live=true pattern=ball ! \
             video/x-raw,width={width},height={height},framerate={fps}/1 ! \
             queue max-size-buffers=1 max-size-bytes=0 max-size-time=0 leaky=downstream ! \
             videoconvert n-threads=4 ! vp8enc deadline=1 cpu-used=8 threads=4 keyframe-max-dist={fps} target-bitrate={bitrate} ! \
             rtpvp8pay pt=96 picture-id-mode=15-bit ! \
             identity name=rtp-counter signal-handoffs=true ! \
             application/x-rtp,media=video,encoding-name=VP8,payload=96,clock-rate=90000 ! sender. ",
            latency = WEBRTC_LATENCY_MS,
            width = quality.width,
            height = quality.height,
            fps = quality.fps,
            bitrate = quality.bitrate_kbps,
        );

        let pipeline = gst::parse::launch(&description)
            .context("failed to construct WebRTC sender pipeline")?
            .downcast::<gst::Pipeline>()
            .map_err(|_| anyhow!("GStreamer launch did not return a pipeline"))?;

        let sender = pipeline
            .by_name("sender")
            .context("sender webrtcbin missing")?;
        let receiver = pipeline
            .by_name("receiver")
            .context("receiver webrtcbin missing")?;
        let rtp_counter = pipeline
            .by_name("rtp-counter")
            .context("RTP counter missing")?;

        let frames = Arc::new(AtomicU64::new(0));
        let rtp_bytes = Arc::new(AtomicU64::new(0));
        let streaming_emitted = Arc::new(AtomicBool::new(false));

        {
            let rtp_bytes = Arc::clone(&rtp_bytes);
            rtp_counter.connect("handoff", false, move |values| {
                if let Ok(buffer) = values[1].get::<gst::Buffer>() {
                    rtp_bytes.fetch_add(buffer.size() as u64, Ordering::Relaxed);
                }
                None
            });
        }

        wire_ice(&sender, &receiver)?;
        wire_ice(&receiver, &sender)?;
        wire_receiver(
            &pipeline,
            &receiver,
            Arc::clone(&frames),
            Arc::clone(&streaming_emitted),
            emitter.clone(),
        );
        wire_negotiation(&sender, &receiver, emitter.clone())?;

        pipeline
            .set_state(gst::State::Playing)
            .map_err(|_| anyhow!("failed to set WebRTC pipeline to Playing"))?;

        Ok(Self {
            pipeline,
            quality,
            frames,
            rtp_bytes,
            last_sample: Instant::now(),
            last_frames: 0,
            last_bytes: 0,
        })
    }

    pub fn stop(self, emitter: &Emitter) {
        let _ = self.pipeline.set_state(gst::State::Null);
        emitter.emit(&Event::State {
            phase: "idle",
            message: "WebRTC session ended cleanly",
        });
    }

    pub fn poll(&mut self, emitter: &Emitter) -> Result<()> {
        if let Some(bus) = self.pipeline.bus() {
            while let Some(message) = bus.timed_pop(gst::ClockTime::ZERO) {
                use gst::MessageView;
                match message.view() {
                    MessageView::Error(error) => {
                        return Err(anyhow!(
                            "{}: {} ({})",
                            error
                                .src()
                                .map(|source| source.path_string())
                                .unwrap_or_else(|| "unknown".into()),
                            error.error(),
                            error.debug().unwrap_or_else(|| "no debug details".into())
                        ));
                    }
                    MessageView::Warning(warning) => {
                        eprintln!(
                            "GStreamer warning from {}: {}",
                            warning
                                .src()
                                .map(|source| source.path_string())
                                .unwrap_or_else(|| "unknown".into()),
                            warning.error()
                        );
                    }
                    _ => {}
                }
            }
        }

        let elapsed = self.last_sample.elapsed();
        if elapsed < Duration::from_secs(1) {
            return Ok(());
        }

        let frames = self.frames.load(Ordering::Relaxed);
        let bytes = self.rtp_bytes.load(Ordering::Relaxed);
        let interval = elapsed.as_secs_f64();
        let fps = ((frames - self.last_frames) as f64 / interval).round() as u64;
        let bitrate_kbps = (((bytes - self.last_bytes) as f64 * 8.0 / 1000.0) / interval) as u64;

        emitter.emit(&Event::Stats {
            codec: "VP8",
            resolution: self.quality.display_resolution(),
            fps,
            bitrate_kbps,
            latency_ms: WEBRTC_LATENCY_MS,
            packet_loss: 0.0,
            frames_decoded: frames,
        });

        self.last_frames = frames;
        self.last_bytes = bytes;
        self.last_sample = Instant::now();
        Ok(())
    }
}

fn wire_ice(source: &gst::Element, destination: &gst::Element) -> Result<()> {
    let destination = destination.downgrade();
    source.connect("on-ice-candidate", false, move |values| {
        let Some(destination) = destination.upgrade() else {
            return None;
        };
        let Ok(index) = values[1].get::<u32>() else {
            return None;
        };
        let Ok(candidate) = values[2].get::<String>() else {
            return None;
        };
        destination.emit_by_name::<()>("add-ice-candidate", &[&index, &candidate]);
        None
    });
    Ok(())
}

fn wire_negotiation(
    sender: &gst::Element,
    receiver: &gst::Element,
    emitter: Emitter,
) -> Result<()> {
    let sender_weak = sender.downgrade();
    let receiver_weak = receiver.downgrade();

    sender.connect("on-negotiation-needed", false, move |_| {
        let (Some(sender), Some(receiver)) = (sender_weak.upgrade(), receiver_weak.upgrade())
        else {
            return None;
        };

        let sender_for_offer = sender.clone();
        let receiver_for_offer = receiver.clone();
        let emitter_for_offer = emitter.clone();
        let promise = gst::Promise::with_change_func(move |reply| {
            let Ok(Some(reply)) = reply else {
                emitter_for_offer.emit(&Event::Error {
                    code: "offer-failed",
                    message: "WebRTC offer did not produce a reply".to_owned(),
                });
                return;
            };
            let Ok(offer) = reply.get::<gst_webrtc::WebRTCSessionDescription>("offer") else {
                return;
            };

            sender_for_offer
                .emit_by_name::<()>("set-local-description", &[&offer, &None::<gst::Promise>]);
            receiver_for_offer
                .emit_by_name::<()>("set-remote-description", &[&offer, &None::<gst::Promise>]);

            let sender_for_answer = sender_for_offer.clone();
            let receiver_for_answer = receiver_for_offer.clone();
            let emitter_for_answer = emitter_for_offer.clone();
            let answer_promise = gst::Promise::with_change_func(move |reply| {
                let Ok(Some(reply)) = reply else {
                    return;
                };
                let Ok(answer) = reply.get::<gst_webrtc::WebRTCSessionDescription>("answer") else {
                    return;
                };
                receiver_for_answer
                    .emit_by_name::<()>("set-local-description", &[&answer, &None::<gst::Promise>]);
                sender_for_answer.emit_by_name::<()>(
                    "set-remote-description",
                    &[&answer, &None::<gst::Promise>],
                );
                emitter_for_answer.emit(&Event::State {
                    phase: "connecting",
                    message: "SDP exchanged; waiting for the first decoded frame",
                });
            });

            receiver_for_offer
                .emit_by_name::<()>("create-answer", &[&None::<gst::Structure>, &answer_promise]);
        });

        sender.emit_by_name::<()>("create-offer", &[&None::<gst::Structure>, &promise]);
        None
    });
    Ok(())
}

fn wire_receiver(
    pipeline: &gst::Pipeline,
    receiver: &gst::Element,
    frames: Arc<AtomicU64>,
    streaming_emitted: Arc<AtomicBool>,
    emitter: Emitter,
) {
    let pipeline = pipeline.downgrade();
    let receiver_linked = Arc::new(AtomicBool::new(false));
    receiver.connect_pad_added(move |_receiver, pad| {
        if receiver_linked.swap(true, Ordering::SeqCst) {
            return;
        }
        let Some(pipeline) = pipeline.upgrade() else {
            return;
        };

        let queue = gst::ElementFactory::make("queue")
            .property("max-size-buffers", 1u32)
            .property("max-size-bytes", 0u32)
            .property("max-size-time", 0u64)
            .property_from_str("leaky", "downstream")
            .build()
            .expect("queue should be installed");
        let depay = gst::ElementFactory::make("rtpvp8depay")
            .build()
            .expect("rtpvp8depay missing");
        let decoder = gst::ElementFactory::make("vp8dec")
            .build()
            .expect("vp8dec missing");
        let counter = gst::ElementFactory::make("identity")
            .property("signal-handoffs", true)
            .build()
            .expect("identity missing");
        let convert = gst::ElementFactory::make("videoconvert")
            .build()
            .expect("videoconvert missing");
        let sink_factory = if cfg!(target_os = "linux") {
            "ximagesink"
        } else {
            "autovideosink"
        };
        let sink = gst::ElementFactory::make(sink_factory)
            .property("sync", false)
            .build()
            .expect("native video sink missing");

        let frames_for_handoff = Arc::clone(&frames);
        let streaming_for_handoff = Arc::clone(&streaming_emitted);
        let emitter_for_handoff = emitter.clone();
        counter.connect("handoff", false, move |_| {
            frames_for_handoff.fetch_add(1, Ordering::Relaxed);
            if !streaming_for_handoff.swap(true, Ordering::SeqCst) {
                emitter_for_handoff.emit(&Event::State {
                    phase: "streaming",
                    message: "Local WebRTC media is live",
                });
            }
            None
        });

        pipeline
            .add_many([&queue, &depay, &decoder, &counter, &convert, &sink])
            .expect("failed to add receiver elements");
        gst::Element::link_many([&queue, &depay, &decoder, &counter, &convert, &sink])
            .expect("failed to link receiver elements");

        let Some(sink_pad) = queue.static_pad("sink") else {
            return;
        };
        if pad.link(&sink_pad).is_err() {
            return;
        }
        for element in [&queue, &depay, &decoder, &counter, &convert, &sink] {
            let _ = element.sync_state_with_parent();
        }
    });
}
