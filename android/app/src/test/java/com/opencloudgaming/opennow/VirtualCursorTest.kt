package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

/**
 * Direct click has no absolute-positioning packet to lean on. Every new tap therefore reanchors the
 * host cursor at its top-left boundary before moving to the mapped target; the retained cursor model
 * is used only for relative movement during that press. These tests cover both the boundary anchor
 * and the resize/rounding properties needed while a drag is active.
 */
class VirtualCursorTest {

    /** Plays the role of the host: applies the deltas we send and reports the true cursor. */
    private class FakeHost(var x: Float, var y: Float) {
        fun apply(delta: CursorDelta?) {
            if (delta == null) return
            x += delta.dx
            y += delta.dy
        }

        fun applyClamped(delta: CursorDelta, width: Int, height: Int) {
            x = (x + delta.dx).coerceIn(0f, (width - 1).toFloat())
            y = (y + delta.dy).coerceIn(0f, (height - 1).toFloat())
        }
    }

    @Test
    fun firstTapOfASessionAnchorsFromTheCentre() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)
        assertEquals(StreamPoint(960f, 540f), cursor.position)
    }

    @Test
    fun tappingLandsExactlyOnTarget() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)
        val host = FakeHost(960f, 540f) // starts where the model assumes

        host.apply(cursor.consumeDeltaTo(StreamPoint(100f, 200f)))

        assertEquals(100f, host.x, 0.5f)
        assertEquals(200f, host.y, 0.5f)
    }

    @Test
    fun directClickReanchorsAnUnknownHostCursorBeforeMovingToTarget() {
        val width = 1920
        val height = 1080
        val cursor = VirtualCursor()
        cursor.onStreamSize(width, height)
        val host = FakeHost(1733f, 941f)

        cursor.reanchorDeltasTo(StreamPoint(100f, 200f)).forEach { delta ->
            host.applyClamped(delta, width, height)
        }

        assertEquals(100f, host.x, 0.5f)
        assertEquals(200f, host.y, 0.5f)
        assertEquals(StreamPoint(100f, 200f), cursor.position)
    }

    @Test
    fun directClickClampsTheMappedBottomRightEdgeToARealDesktopPixel() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)

        cursor.reanchorDeltasTo(StreamPoint(1920f, 1080f))

        assertEquals(StreamPoint(1919f, 1079f), cursor.position)
    }

    /**
     * The PiP regression itself. Nothing about a window resize reaches this class, so the only way
     * to state it is: repeated size notifications must not disturb a model that is already correct.
     */
    @Test
    fun repeatedStreamSizeNotificationsDoNotDisturbTheModel() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)
        val host = FakeHost(960f, 540f)

        // User moves the cursor to the bottom-right corner.
        host.apply(cursor.consumeDeltaTo(StreamPoint(1900f, 1060f)))
        assertEquals(1900f, host.x, 0.5f)

        // Enter PiP, leave PiP, rotate — each of which re-enters handle() and re-reports the
        // stream size. The model must be untouched.
        repeat(5) { cursor.onStreamSize(1920, 1080) }
        assertEquals(StreamPoint(1900f, 1060f), cursor.position)

        // The next tap, in the opposite corner, must still land on target.
        host.apply(cursor.consumeDeltaTo(StreamPoint(20f, 30f)))
        assertEquals(20f, host.x, 0.5f)
        assertEquals(30f, host.y, 0.5f)
    }

    @Test
    fun resolutionChangeRescalesTheModelRatherThanGuessing() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)
        val host = FakeHost(960f, 540f)

        // Three quarters across, one quarter down.
        host.apply(cursor.consumeDeltaTo(StreamPoint(1440f, 270f)))

        // The host switches to 1280x720; its cursor keeps the same relative position.
        host.x = host.x / 1920f * 1280f
        host.y = host.y / 1080f * 720f
        cursor.onStreamSize(1280, 720)

        assertEquals(960f, cursor.position.x, 0.5f)
        assertEquals(180f, cursor.position.y, 0.5f)

        // And a tap in the new space still lands where asked.
        host.apply(cursor.consumeDeltaTo(StreamPoint(100f, 600f)))
        assertEquals(100f, host.x, 0.5f)
        assertEquals(600f, host.y, 0.5f)
    }

    @Test
    fun forgettingReAnchorsOnTheNextStreamSize() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)
        cursor.consumeDeltaTo(StreamPoint(1900f, 1060f))

        cursor.forget()
        cursor.onStreamSize(1920, 1080)

        assertEquals(StreamPoint(960f, 540f), cursor.position)
    }

    @Test
    fun subPixelMovesAreNotSent() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)

        assertNull(cursor.consumeDeltaTo(StreamPoint(960.2f, 540.1f)))
        // And the model did not drift by the rejected fraction either.
        assertEquals(StreamPoint(960f, 540f), cursor.position)
    }

    /**
     * The wire format carries whole pixels, so each move rounds. Advancing the model by the target
     * instead of by what was sent would absorb that residue every event and let the error compound;
     * over a long drag that is the "cursor drifts away" symptom.
     */
    @Test
    fun errorDoesNotAccumulateOverALongDrag() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(1920, 1080)
        val host = FakeHost(960f, 540f)

        var target = 960f
        repeat(500) {
            target += 0.37f // deliberately fractional, so every step has a rounding residue
            host.apply(cursor.consumeDeltaTo(StreamPoint(target, 540f)))
        }

        // Bounded by a single rounding, not by 500 of them.
        assertEquals(
            "host drifted from the requested position by ${abs(host.x - target)}px",
            target,
            host.x,
            1f,
        )
    }

    @Test
    fun degenerateStreamSizeIsIgnored() {
        val cursor = VirtualCursor()
        cursor.onStreamSize(0, 1080)
        cursor.onStreamSize(1920, 0)
        // Still uninitialised, so the first real size anchors normally.
        cursor.onStreamSize(1920, 1080)
        assertEquals(StreamPoint(960f, 540f), cursor.position)
    }
}
