use std::sync::Mutex;

use crate::queue::{BoundedQueue, PushResult};
use crate::ring::PcmRing;

pub(crate) struct AudioPacket {
    pub(crate) data: Vec<u8>,
    generation: u64,
}

pub(crate) struct AudioQueue {
    packets: BoundedQueue<AudioPacket>,
    pcm: PcmRing,
    generation: Mutex<u64>,
}

impl AudioQueue {
    pub(crate) fn new(packet_capacity: usize, pcm_capacity: usize) -> Self {
        Self {
            packets: BoundedQueue::new(packet_capacity),
            pcm: PcmRing::new(pcm_capacity),
            generation: Mutex::new(0),
        }
    }

    pub(crate) fn submit(&self, data: Vec<u8>) -> PushResult<Vec<u8>> {
        let generation = self.generation.lock().unwrap_or_else(|p| p.into_inner());
        match self.packets.push_drop_oldest(AudioPacket {
            data,
            generation: *generation,
        }) {
            PushResult::Pushed => PushResult::Pushed,
            PushResult::Replaced(packet) => PushResult::Replaced(packet.data),
            PushResult::Closed(packet) => PushResult::Closed(packet.data),
        }
    }

    pub(crate) fn pop_wait(&self) -> Option<AudioPacket> {
        self.packets.pop_wait()
    }

    pub(crate) fn publish_pcm(&self, packet: &AudioPacket, pcm: &[f32]) -> usize {
        let generation = self.generation.lock().unwrap_or_else(|p| p.into_inner());
        if packet.generation != *generation {
            return 0;
        }
        self.pcm.push(pcm)
    }

    pub(crate) fn pop_pcm(&self, output: &mut [f32]) -> usize {
        self.pcm.pop_into(output)
    }

    pub(crate) fn clear(&self) {
        let mut generation = self.generation.lock().unwrap_or_else(|p| p.into_inner());
        *generation = generation.wrapping_add(1);
        self.packets.clear();
        self.pcm.clear();
    }

    pub(crate) fn close(&self) {
        self.packets.close_and_discard();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Arc, Barrier};
    use std::thread;

    #[test]
    fn pause_and_resume_discard_a_packet_still_being_decoded() {
        let queue = AudioQueue::new(2, 8);
        queue.submit(vec![1]);
        let in_flight = queue.pop_wait().unwrap();
        queue.clear();
        queue.clear();
        assert_eq!(queue.publish_pcm(&in_flight, &[1.0, 2.0]), 0);
        queue.submit(vec![2]);
        let resumed = queue.pop_wait().unwrap();
        assert_eq!(queue.publish_pcm(&resumed, &[3.0, 4.0]), 2);
        let mut output = [0.0; 8];
        assert_eq!(queue.pop_pcm(&mut output), 2);
        assert_eq!(&output[..2], &[3.0, 4.0]);
    }

    #[test]
    fn clear_discards_pending_packets_and_published_pcm() {
        let queue = AudioQueue::new(2, 8);
        queue.submit(vec![1]);
        let packet = queue.pop_wait().unwrap();
        assert_eq!(queue.publish_pcm(&packet, &[1.0, 2.0]), 2);
        queue.submit(vec![2]);
        queue.clear();
        assert_eq!(queue.pop_pcm(&mut [0.0; 8]), 0);
        queue.submit(vec![3]);
        assert_eq!(queue.pop_wait().unwrap().data, vec![3]);
    }

    #[test]
    fn clear_serializes_with_concurrent_pcm_publication() {
        for _ in 0..100 {
            let queue = Arc::new(AudioQueue::new(2, 8));
            queue.submit(vec![1]);
            let packet = queue.pop_wait().unwrap();
            let barrier = Arc::new(Barrier::new(2));
            let worker = {
                let queue = Arc::clone(&queue);
                let barrier = Arc::clone(&barrier);
                thread::spawn(move || {
                    barrier.wait();
                    queue.publish_pcm(&packet, &[1.0, 2.0]);
                })
            };
            barrier.wait();
            queue.clear();
            worker.join().unwrap();
            assert_eq!(queue.pop_pcm(&mut [0.0; 8]), 0);
        }
    }

    #[test]
    fn queue_remains_bounded_and_close_discards_pending_work() {
        let queue = AudioQueue::new(1, 2);
        assert!(matches!(queue.submit(vec![1]), PushResult::Pushed));
        assert!(matches!(queue.submit(vec![2]), PushResult::Replaced(data) if data == vec![1]));
        let packet = queue.pop_wait().unwrap();
        assert_eq!(queue.publish_pcm(&packet, &[1.0, 2.0, 3.0]), 2);
        queue.submit(vec![3]);
        queue.close();
        assert!(queue.pop_wait().is_none());
        assert!(matches!(queue.submit(vec![4]), PushResult::Closed(data) if data == vec![4]));
    }
}
