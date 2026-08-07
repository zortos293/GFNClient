package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-state coverage for the mouse-move coalescing window: accumulation, merge of deltas and of
 * reliability, and the dirty/flush lifecycle that drives packet scheduling in NativeStreamClient.
 */
class MouseMoveCoalescerTest {

    @Test
    fun emptyCoalescerNeverNeedsFlush() {
        val coalescer = MouseMoveCoalescer()
        assertFalse(coalescer.needsFlush)
        assertNull(coalescer.flush())
        assertFalse(coalescer.needsFlush)
    }

    @Test
    fun addingADeltaMarksTheWindowDirtyOnce() {
        val coalescer = MouseMoveCoalescer()
        assertFalse(coalescer.needsFlush)
        coalescer.add(1, 2, partiallyReliable = true)
        assertTrue(coalescer.needsFlush)
        // More adds inside the same window keep it dirty but still one single flush.
        coalescer.add(3, 4, partiallyReliable = true)
        assertTrue(coalescer.needsFlush)
    }

    @Test
    fun flushSumsEveryDeltaInTheWindow() {
        val coalescer = MouseMoveCoalescer()
        coalescer.add(10, 20, partiallyReliable = true)
        coalescer.add(5, -7, partiallyReliable = true)
        coalescer.add(-3, 2, partiallyReliable = true)

        val batch = coalescer.flush()
        assertEquals(12, batch?.dx)
        assertEquals(15, batch?.dy)
        assertEquals(true, batch?.partiallyReliable)

        assertFalse(coalescer.needsFlush)
        assertNull(coalescer.flush())
    }

    @Test
    fun zeroDeltasDoNotDirtyTheWindow() {
        val coalescer = MouseMoveCoalescer()
        coalescer.add(0, 0, partiallyReliable = true)
        assertFalse(coalescer.needsFlush)

        // A zero delta never schedules a flush, but a later real one does, and the batch keeps
        // the zero contribution.
        coalescer.add(0, 5, partiallyReliable = true)
        assertTrue(coalescer.needsFlush)
        val batch = coalescer.flush()
        assertEquals(0, batch?.dx)
        assertEquals(5, batch?.dy)
    }

    @Test
    fun negativeDeltasAccumulateIntoTheSameBatch() {
        val coalescer = MouseMoveCoalescer()
        coalescer.add(-100, -50, partiallyReliable = true)
        coalescer.add(30, 10, partiallyReliable = true)
        val batch = coalescer.flush()
        assertEquals(-70, batch?.dx)
        assertEquals(-40, batch?.dy)
    }

    @Test
    fun partiallyReliableIsTheAndOfEveryAddInTheWindow() {
        val allPartial = MouseMoveCoalescer()
        allPartial.add(1, 1, partiallyReliable = true)
        allPartial.add(2, 2, partiallyReliable = true)
        assertEquals(true, allPartial.flush()?.partiallyReliable)

        // One reliable request makes the whole batch reliable — never silently downgraded.
        val mixed = MouseMoveCoalescer()
        mixed.add(1, 1, partiallyReliable = true)
        mixed.add(2, 2, partiallyReliable = false)
        assertEquals(false, mixed.flush()?.partiallyReliable)
    }

    @Test
    fun flushResetsReliabilityForTheNextWindow() {
        val coalescer = MouseMoveCoalescer()
        coalescer.add(1, 1, partiallyReliable = false)
        assertEquals(false, coalescer.flush()?.partiallyReliable)

        coalescer.add(2, 2, partiallyReliable = true)
        assertEquals(true, coalescer.flush()?.partiallyReliable)
    }

    @Test
    fun zeroZeroAddCannotPinTheWindowToReliable() {
        val coalescer = MouseMoveCoalescer()
        // A dropped-in (0, 0) with reliable intent must not leak into the next real window.
        coalescer.add(0, 0, partiallyReliable = false)
        assertFalse(coalescer.needsFlush)

        coalescer.add(5, 5, partiallyReliable = true)
        assertEquals(true, coalescer.flush()?.partiallyReliable)
    }

    @Test
    fun flushClearsTheWindowAndAllowsANewDirtyWindow() {
        val coalescer = MouseMoveCoalescer()
        coalescer.add(7, 7, partiallyReliable = true)
        coalescer.flush()

        assertFalse(coalescer.needsFlush)
        coalescer.add(1, 1, partiallyReliable = true)
        assertTrue(coalescer.needsFlush)
        val batch = coalescer.flush()
        assertEquals(1, batch?.dx)
        assertEquals(1, batch?.dy)
    }
}
