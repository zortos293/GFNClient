use std::collections::VecDeque;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread::{self, JoinHandle};

use opus::{Application, Channels, Encoder};
use sdl2::audio::{AudioCallback, AudioDevice, AudioSpecDesired};

use crate::queue::BoundedQueue;

const SAMPLE_RATE: u32 = 48_000;
const FRAME_SAMPLES: usize = 960;
const PCM_QUEUE_CAPACITY: usize = 8;
const PACKET_QUEUE_CAPACITY: usize = 4;
const MAX_OPUS_PACKET_BYTES: usize = 4_000;

#[derive(Debug, Clone)]
pub struct EncodedMicrophonePacket {
    pub payload: Arc<[u8]>,
    pub captured_at_us: u64,
    pub audio_level_db: i8,
    pub voice_activity: bool,
}

#[derive(Debug, Default)]
pub struct EncodedMicrophoneQueue {
    pending: Mutex<VecDeque<EncodedMicrophonePacket>>,
    dropped: AtomicU64,
}

impl EncodedMicrophoneQueue {
    fn push(&self, packet: EncodedMicrophonePacket) {
        let mut pending = self
            .pending
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if pending.len() == PACKET_QUEUE_CAPACITY {
            pending.pop_front();
            self.dropped.fetch_add(1, Ordering::Relaxed);
        }
        pending.push_back(packet);
    }

    pub fn take(&self) -> Option<EncodedMicrophonePacket> {
        self.pending
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .pop_front()
    }

    pub fn take_dropped(&self) -> u64 {
        self.dropped.swap(0, Ordering::AcqRel)
    }

    pub fn clear(&self) {
        self.pending
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .clear();
        self.dropped.store(0, Ordering::Release);
    }
}

struct CaptureCallback {
    pcm: Arc<BoundedQueue<Vec<i16>>>,
}

impl AudioCallback for CaptureCallback {
    type Channel = i16;

    fn callback(&mut self, input: &mut [i16]) {
        self.pcm.push(input.to_vec());
    }
}

pub struct MicrophoneCapture {
    device: AudioDevice<CaptureCallback>,
    pcm: Arc<BoundedQueue<Vec<i16>>>,
    packets: Arc<EncodedMicrophoneQueue>,
    worker: Option<JoinHandle<()>>,
    _sdl: sdl2::Sdl,
}

impl MicrophoneCapture {
    pub fn device_names() -> Result<Vec<String>, String> {
        let sdl = sdl2::init().map_err(|error| format!("SDL initialization failed: {error}"))?;
        let audio = sdl
            .audio()
            .map_err(|error| format!("SDL audio initialization failed: {error}"))?;
        let count = audio.num_audio_capture_devices().unwrap_or_default();
        Ok((0..count)
            .filter_map(|index| audio.audio_capture_device_name(index).ok())
            .filter(|name| !name.trim().is_empty())
            .collect())
    }

    pub fn start(device_name: Option<&str>) -> Result<Self, String> {
        let sdl = sdl2::init().map_err(|error| format!("SDL initialization failed: {error}"))?;
        let audio = sdl
            .audio()
            .map_err(|error| format!("SDL audio initialization failed: {error}"))?;
        let pcm = Arc::new(BoundedQueue::new(PCM_QUEUE_CAPACITY));
        let desired = AudioSpecDesired {
            freq: Some(SAMPLE_RATE as i32),
            channels: Some(1),
            samples: Some(FRAME_SAMPLES as u16),
        };
        let callback_pcm = Arc::clone(&pcm);
        let device = audio
            .open_capture(
                device_name.filter(|value| !value.trim().is_empty()),
                &desired,
                move |_| CaptureCallback { pcm: callback_pcm },
            )
            .map_err(|error| format!("microphone capture could not start: {error}"))?;
        if device.spec().freq != SAMPLE_RATE as i32 || device.spec().channels != 1 {
            return Err(format!(
                "microphone returned unsupported format: {} Hz, {} channels",
                device.spec().freq,
                device.spec().channels
            ));
        }
        let packets = Arc::new(EncodedMicrophoneQueue::default());
        let worker_pcm = Arc::clone(&pcm);
        let worker_packets = Arc::clone(&packets);
        let worker = thread::Builder::new()
            .name("opennow-microphone-opus".to_owned())
            .spawn(move || encode_worker(worker_pcm, worker_packets))
            .map_err(|error| format!("microphone encoder worker could not start: {error}"))?;
        device.resume();
        Ok(Self {
            device,
            pcm,
            packets,
            worker: Some(worker),
            _sdl: sdl,
        })
    }

    pub fn packets(&self) -> Arc<EncodedMicrophoneQueue> {
        Arc::clone(&self.packets)
    }

    pub fn set_enabled(&self, enabled: bool) {
        if enabled {
            self.device.resume();
        } else {
            self.device.pause();
            self.packets.clear();
        }
    }
}

impl Drop for MicrophoneCapture {
    fn drop(&mut self) {
        self.device.pause();
        self.pcm.close();
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
        self.packets.clear();
    }
}

fn encode_worker(pcm: Arc<BoundedQueue<Vec<i16>>>, packets: Arc<EncodedMicrophoneQueue>) {
    let Ok(mut encoder) = Encoder::new(SAMPLE_RATE, Channels::Mono, Application::Voip) else {
        return;
    };
    let mut accumulated = VecDeque::with_capacity(FRAME_SAMPLES * 2);
    let mut output = vec![0_u8; MAX_OPUS_PACKET_BYTES];
    let mut sample_cursor = 0_u64;
    while let Some(samples) = pcm.pop() {
        accumulated.extend(samples);
        while accumulated.len() >= FRAME_SAMPLES {
            let frame = accumulated.drain(..FRAME_SAMPLES).collect::<Vec<_>>();
            let Ok(length) = encoder.encode(&frame, &mut output) else {
                continue;
            };
            let (audio_level_db, voice_activity) = audio_level(&frame);
            packets.push(EncodedMicrophonePacket {
                payload: Arc::from(&output[..length]),
                captured_at_us: sample_cursor.saturating_mul(1_000_000) / u64::from(SAMPLE_RATE),
                audio_level_db,
                voice_activity,
            });
            sample_cursor = sample_cursor.saturating_add(FRAME_SAMPLES as u64);
        }
    }
}

fn audio_level(samples: &[i16]) -> (i8, bool) {
    let mean_square = samples
        .iter()
        .map(|sample| {
            let normalized = f64::from(*sample) / 32768.0;
            normalized * normalized
        })
        .sum::<f64>()
        / samples.len().max(1) as f64;
    let db = if mean_square <= f64::EPSILON {
        -127.0
    } else {
        20.0 * mean_square.sqrt().log10()
    };
    let bounded = db.round().clamp(-127.0, 0.0) as i8;
    (bounded, bounded > -60)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn microphone_queue_is_bounded_and_levels_are_stable() {
        let queue = EncodedMicrophoneQueue::default();
        for index in 0..6_u64 {
            queue.push(EncodedMicrophonePacket {
                payload: Arc::from([index as u8]),
                captured_at_us: index,
                audio_level_db: -30,
                voice_activity: true,
            });
        }
        assert_eq!(queue.take_dropped(), 2);
        assert_eq!(queue.take().unwrap().captured_at_us, 2);
        assert_eq!(audio_level(&[0; FRAME_SAMPLES]), (-127, false));
        assert!(audio_level(&[10_000; FRAME_SAMPLES]).1);
    }

    #[test]
    fn microphone_pcm_encodes_to_opus() {
        let pcm = Arc::new(BoundedQueue::new(2));
        let packets = Arc::new(EncodedMicrophoneQueue::default());
        let worker_pcm = Arc::clone(&pcm);
        let worker_packets = Arc::clone(&packets);
        let worker = thread::spawn(move || encode_worker(worker_pcm, worker_packets));
        pcm.push(vec![0_i16; FRAME_SAMPLES]);
        let packet = (0..100)
            .find_map(|_| {
                let packet = packets.take();
                if packet.is_none() {
                    thread::sleep(std::time::Duration::from_millis(5));
                }
                packet
            })
            .expect("encoded microphone packet");
        pcm.close();
        worker.join().expect("microphone worker");
        assert!(!packet.payload.is_empty());
        assert_eq!(packet.captured_at_us, 0);
    }
}
