use std::sync::Mutex;
use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Default)]
pub(crate) struct FrameOrder {
    next_submission: AtomicU64,
    last_published: Mutex<Option<u64>>,
}

impl FrameOrder {
    pub(crate) fn submit(&self) -> u64 {
        self.next_submission.fetch_add(1, Ordering::Relaxed)
    }

    pub(crate) fn publish<T>(&self, submission: u64, publish: impl FnOnce() -> T) -> Option<T> {
        let mut last = self
            .last_published
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner());
        if last.is_some_and(|last| submission <= last) {
            return None;
        }
        let result = publish();
        *last = Some(submission);
        Some(result)
    }
}

#[cfg(test)]
mod tests {
    use super::FrameOrder;
    use std::sync::{Arc, Mutex};

    #[test]
    fn late_and_duplicate_callbacks_cannot_replace_newer_output() {
        let order = FrameOrder::default();
        let first = order.submit();
        let second = order.submit();
        let third = order.submit();
        assert_eq!(order.publish(second, || 2), Some(2));
        assert_eq!(
            order.publish(first, || panic!("stale frame published")),
            None::<()>
        );
        assert_eq!(
            order.publish(second, || panic!("duplicate frame published")),
            None::<()>
        );
        assert_eq!(order.publish(third, || 3), Some(3));
    }

    #[test]
    fn sender_timestamp_wrap_does_not_block_new_submissions() {
        let order = FrameOrder::default();
        let before_wrap = order.submit();
        let after_wrap = order.submit();
        assert_eq!(order.publish(before_wrap, || u32::MAX), Some(u32::MAX));
        assert_eq!(order.publish(after_wrap, || 0), Some(0));
    }

    #[test]
    fn failed_submissions_and_new_sessions_do_not_block_output() {
        let order = FrameOrder::default();
        order.submit();
        let next = order.submit();
        assert_eq!(order.publish(next, || 1), Some(1));
        let replacement = FrameOrder::default();
        let first = replacement.submit();
        assert_eq!(replacement.publish(first, || 2), Some(2));
    }

    #[test]
    fn concurrent_callbacks_publish_in_increasing_submission_order() {
        let order = Arc::new(FrameOrder::default());
        let published = Arc::new(Mutex::new(Vec::new()));
        let submissions: Vec<_> = (0..32).map(|_| order.submit()).collect();
        std::thread::scope(|scope| {
            for submission in submissions.into_iter().rev() {
                let order = Arc::clone(&order);
                let published = Arc::clone(&published);
                scope.spawn(move || {
                    order.publish(submission, || published.lock().unwrap().push(submission));
                });
            }
        });
        let published = published.lock().unwrap();
        assert!(!published.is_empty());
        assert!(published.windows(2).all(|pair| pair[0] < pair[1]));
        assert_eq!(published.last(), Some(&31));
    }
}
