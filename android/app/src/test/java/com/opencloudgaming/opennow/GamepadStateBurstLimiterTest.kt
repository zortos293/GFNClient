package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadStateBurstLimiterTest {
    @Test
    fun sendsLeadingSnapshotAndCoalescesBurstToLatestController() {
        val limiter = GamepadStateBurstLimiter(minimumIntervalMs = 16)

        assertEquals(0, limiter.offer(controllerId = 0, nowMs = 100))
        assertNull(limiter.offer(controllerId = 0, nowMs = 104))
        assertNull(limiter.offer(controllerId = 1, nowMs = 108))
        assertEquals(8L, limiter.delayUntilFlushMs(nowMs = 108))
        assertEquals(1, limiter.flush(nowMs = 116))
        assertNull(limiter.flush(nowMs = 116))
    }

    @Test
    fun idleSnapshotSendsImmediately() {
        val limiter = GamepadStateBurstLimiter(minimumIntervalMs = 16)

        assertEquals(0, limiter.offer(controllerId = 0, nowMs = 100))
        assertEquals(0, limiter.offer(controllerId = 0, nowMs = 116))
        assertNull(limiter.delayUntilFlushMs(nowMs = 116))
    }

    @Test
    fun resetClearsPendingSnapshotAndTiming() {
        val limiter = GamepadStateBurstLimiter(minimumIntervalMs = 16)

        limiter.offer(controllerId = 0, nowMs = 100)
        limiter.offer(controllerId = 1, nowMs = 104)
        limiter.reset()

        assertNull(limiter.flush(nowMs = 105))
        assertEquals(1, limiter.offer(controllerId = 1, nowMs = 105))
    }
}
