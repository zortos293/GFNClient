package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MouseMotionAccumulatorTest {
    @Test
    fun externalMouseKeepsVerySlowFractionalRelativeMotion() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 0L)
        val sent = mutableListOf<MouseMotionDelta>()

        repeat(20) { index ->
            accumulator.add(
                dx = 0.2f,
                dy = -0.1f,
                eventTimeMs = index.toLong(),
                sensitivity = 1f,
                acceleration = 1,
            )?.let(sent::add)
        }

        assertEquals(4, sent.sumOf { it.dx })
        assertEquals(-2, sent.sumOf { it.dy })
    }

    @Test
    fun externalMouseFastMotionRemainsLinearWithoutAcceleration() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 0L)
        val sent = mutableListOf<MouseMotionDelta>()

        repeat(10) { index ->
            accumulator.add(
                dx = 12.5f,
                dy = -7.5f,
                eventTimeMs = index.toLong(),
                sensitivity = 1f,
                acceleration = 1,
            )?.let(sent::add)
        }

        assertEquals(125, sent.sumOf { it.dx })
        assertEquals(-75, sent.sumOf { it.dy })
    }

    @Test
    fun preservesFractionalMotionAcrossHighFrequencyEvents() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 8L)
        val sent = mutableListOf<MouseMotionDelta>()

        repeat(20) { index ->
            accumulator.add(
                dx = 0.2f,
                dy = -0.1f,
                eventTimeMs = index.toLong(),
                sensitivity = 1f,
                acceleration = 1,
            )?.let(sent::add)
        }
        accumulator.add(
            dx = 0f,
            dy = 0f,
            eventTimeMs = 20L,
            sensitivity = 1f,
            acceleration = 1,
            force = true,
        )?.let(sent::add)

        assertEquals(4, sent.sumOf { it.dx })
        assertEquals(-2, sent.sumOf { it.dy })
        assertEquals(true, sent.size < 20)
    }

    @Test
    fun externalMouseKeepsSlowMotionAtReducedSensitivity() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 0L)
        val sent = mutableListOf<MouseMotionDelta>()

        repeat(8) { index ->
            accumulator.add(
                dx = 1f,
                dy = 0f,
                eventTimeMs = index.toLong(),
                sensitivity = 0.25f,
                acceleration = 1,
            )?.let(sent::add)
        }

        assertEquals(2, sent.sumOf { it.dx })
        assertEquals(0, sent.sumOf { it.dy })
    }

    @Test
    fun coalescesEventsInsideSendInterval() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 8L)

        assertEquals(
            MouseMotionDelta(1, 0),
            accumulator.add(1f, 0f, eventTimeMs = 0L, sensitivity = 1f, acceleration = 1),
        )
        assertNull(
            accumulator.add(1f, 0f, eventTimeMs = 3L, sensitivity = 1f, acceleration = 1),
        )
        assertEquals(
            MouseMotionDelta(2, 0),
            accumulator.add(1f, 0f, eventTimeMs = 8L, sensitivity = 1f, acceleration = 1),
        )
    }

    @Test
    fun resetDoesNotLeakResidualMotionIntoNextGesture() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 8L)

        assertNull(
            accumulator.add(0.4f, 0f, eventTimeMs = 0L, sensitivity = 1f, acceleration = 1),
        )
        accumulator.reset()
        assertNull(
            accumulator.add(0.2f, 0f, eventTimeMs = 1L, sensitivity = 1f, acceleration = 1),
        )
    }

    @Test
    fun nonFiniteMotionIsDroppedAndDoesNotPoisonNextGesture() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 0L)

        assertNull(
            accumulator.add(Float.NaN, 1f, eventTimeMs = 0L, sensitivity = 1f, acceleration = 1),
        )
        assertNull(
            accumulator.add(1f, Float.POSITIVE_INFINITY, eventTimeMs = 1L, sensitivity = 1f, acceleration = 1),
        )
        assertNull(
            accumulator.add(1f, 1f, eventTimeMs = 2L, sensitivity = Float.NaN, acceleration = 1),
        )
        assertEquals(
            MouseMotionDelta(2, -1),
            accumulator.add(2f, -1f, eventTimeMs = 3L, sensitivity = 1f, acceleration = 1),
        )
    }

    @Test
    fun overflowingMotionIsDroppedBeforeRounding() {
        val accumulator = MouseMotionAccumulator(minimumSendIntervalMs = 0L)

        assertNull(
            accumulator.add(Float.MAX_VALUE, 0f, eventTimeMs = 0L, sensitivity = Float.MAX_VALUE, acceleration = 1),
        )
        assertEquals(
            MouseMotionDelta(1, 0),
            accumulator.add(1f, 0f, eventTimeMs = 1L, sensitivity = 1f, acceleration = 1),
        )
    }
}
