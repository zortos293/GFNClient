package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MouseMoveBurstLimiterTest {
    @Test
    fun firstMovementIsNeverDelayed() {
        val limiter = MouseMoveBurstLimiter(minimumIntervalMs = 8L)

        assertEquals(MouseMoveBatch(4, -2, true), limiter.offer(4, -2, true, nowMs = 100L))
        assertFalse(limiter.hasPendingMovement)
    }

    @Test
    fun highRateMovementBecomesOneTrailingPacket() {
        val limiter = MouseMoveBurstLimiter(minimumIntervalMs = 8L)
        limiter.offer(1, 1, true, nowMs = 100L)

        assertNull(limiter.offer(2, 3, true, nowMs = 102L))
        assertNull(limiter.offer(4, -1, true, nowMs = 104L))
        assertTrue(limiter.hasPendingMovement)
        assertEquals(4L, limiter.delayUntilFlushMs(nowMs = 104L))
        assertEquals(MouseMoveBatch(6, 2, true), limiter.flush(nowMs = 108L))
    }

    @Test
    fun movementAtTheNextIntervalFlushesPendingAndCurrentTogether() {
        val limiter = MouseMoveBurstLimiter(minimumIntervalMs = 8L)
        limiter.offer(1, 0, true, nowMs = 100L)
        limiter.offer(2, 0, true, nowMs = 103L)

        assertEquals(MouseMoveBatch(5, 0, true), limiter.offer(3, 0, true, nowMs = 108L))
        assertFalse(limiter.hasPendingMovement)
    }

    @Test
    fun reliableMovementKeepsTheCombinedPacketReliable() {
        val limiter = MouseMoveBurstLimiter(minimumIntervalMs = 8L)
        limiter.offer(1, 0, true, nowMs = 100L)
        limiter.offer(2, 0, true, nowMs = 102L)
        limiter.offer(3, 0, false, nowMs = 104L)

        assertEquals(false, limiter.flush(nowMs = 108L)?.partiallyReliable)
    }

    @Test
    fun resetDropsMovementFromThePreviousTransport() {
        val limiter = MouseMoveBurstLimiter(minimumIntervalMs = 8L)
        limiter.offer(1, 0, true, nowMs = 100L)
        limiter.offer(2, 0, true, nowMs = 102L)

        limiter.reset()

        assertFalse(limiter.hasPendingMovement)
        assertNull(limiter.flush(nowMs = 108L))
        assertEquals(MouseMoveBatch(7, 0, true), limiter.offer(7, 0, true, nowMs = 109L))
    }
    @Test
    fun thousandHertzRelativeMousePreservesEveryDeltaWithBoundedPacketRate() {
        val limiter = MouseMoveBurstLimiter(8L)
        val batches = mutableListOf<MouseMoveBatch>()
        repeat(1000) { time ->
            limiter.offer(3, -2, partiallyReliable = false, nowMs = time.toLong())?.let(batches::add)
        }
        limiter.flush(1000L)?.let(batches::add)
        assertEquals(3000, batches.sumOf { it.dx })
        assertEquals(-2000, batches.sumOf { it.dy })
        assertTrue(batches.size <= 126)
        assertTrue(batches.all { !it.partiallyReliable })
    }

    @Test
    fun buttonBoundaryFlushesPendingMotionBeforeTheClick() {
        val limiter = MouseMoveBurstLimiter(8L)
        limiter.offer(1, 0, false, 0L)
        limiter.offer(5, -2, false, 1L)
        assertEquals(MouseMoveBatch(5, -2, false), limiter.flush(2L))
        assertNull(limiter.flush(8L))
    }
}
