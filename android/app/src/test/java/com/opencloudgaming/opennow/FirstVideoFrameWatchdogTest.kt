package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstVideoFrameWatchdogTest {
    @Test
    fun recoversWhenPacketsArriveWithoutRenderedFrame() {
        val watchdog = FirstVideoFrameWatchdog(timeoutMs = 8_000L)

        assertFalse(watchdog.shouldRecover(1_000L, bytesReceived = 1L, connected = true))
        assertFalse(watchdog.shouldRecover(8_999L, bytesReceived = 10_000L, connected = true))
        assertTrue(watchdog.shouldRecover(9_000L, bytesReceived = 20_000L, connected = true))
    }

    @Test
    fun renderedFrameDisarmsRecovery() {
        val watchdog = FirstVideoFrameWatchdog(timeoutMs = 1_000L)

        assertFalse(watchdog.shouldRecover(100L, bytesReceived = 1L, connected = true))
        watchdog.markRendered()
        assertFalse(watchdog.shouldRecover(5_000L, bytesReceived = 50_000L, connected = true))
    }

    @Test
    fun disconnectResetsPendingTimeout() {
        val watchdog = FirstVideoFrameWatchdog(timeoutMs = 1_000L)

        assertFalse(watchdog.shouldRecover(100L, bytesReceived = 1L, connected = true))
        assertFalse(watchdog.shouldRecover(2_000L, bytesReceived = 1L, connected = false))
        assertFalse(watchdog.shouldRecover(2_100L, bytesReceived = 2L, connected = true))
    }
}
