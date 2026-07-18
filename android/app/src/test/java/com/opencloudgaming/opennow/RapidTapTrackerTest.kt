package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidTapTrackerTest {
    @Test
    fun triggersOnTenthTapWithinWindow() {
        val tracker = RapidTapTracker()

        repeat(9) { index ->
            assertFalse(tracker.recordTap(index * 500L))
        }

        assertTrue(tracker.recordTap(4_500L))
        assertFalse(tracker.recordTap(5_000L))
    }

    @Test
    fun expiredTapsDoNotCountTowardSequence() {
        val tracker = RapidTapTracker(requiredTapCount = 3, windowMs = 1_000L)

        assertFalse(tracker.recordTap(0L))
        assertFalse(tracker.recordTap(500L))
        assertFalse(tracker.recordTap(1_400L))
        assertTrue(tracker.recordTap(1_500L))
    }
}
