package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDetailsDismissGestureTest {
    @Test
    fun reachingTopDuringScrollRequiresANewPullBeforeDismissal() {
        val gate = SheetDismissGestureGate()

        assertEquals(0f, gate.dismissDelta(childConsumedY = 36f, availableY = 0f))
        assertEquals(0f, gate.dismissDelta(childConsumedY = 8f, availableY = 12f))
        assertEquals(0f, gate.dismissDelta(childConsumedY = 0f, availableY = 24f))

        gate.reset()

        assertEquals(24f, gate.dismissDelta(childConsumedY = 0f, availableY = 24f))
    }

    @Test
    fun pullThatStartsAtTopCanDismissImmediately() {
        val gate = SheetDismissGestureGate()

        assertEquals(18f, gate.dismissDelta(childConsumedY = 0f, availableY = 18f))
        assertEquals(0f, gate.dismissDelta(childConsumedY = 0f, availableY = -10f))
    }
}
