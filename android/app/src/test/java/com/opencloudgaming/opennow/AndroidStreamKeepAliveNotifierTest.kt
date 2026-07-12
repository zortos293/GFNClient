package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStreamKeepAliveNotifierTest {
    @Test
    fun keepsReadyStreamAlive() {
        val state = OpenNowUiState(
            page = AppPage.Stream,
            streamStatus = "streaming",
            streamSession = readySession(),
        )

        assertTrue(shouldKeepAndroidStreamAlive(state))
    }

    @Test
    fun doesNotKeepQueueOrExitedStreamAlive() {
        assertFalse(
            shouldKeepAndroidStreamAlive(
                OpenNowUiState(page = AppPage.Stream, streamStatus = "queueing"),
            ),
        )
        assertFalse(
            shouldKeepAndroidStreamAlive(
                OpenNowUiState(page = AppPage.Home, streamStatus = "streaming", streamSession = readySession()),
            ),
        )
    }

    private fun readySession(): SessionInfo = SessionInfo(
        sessionId = "session-id",
        status = 2,
        serverIp = "example.invalid",
        signalingServer = "example.invalid",
        signalingUrl = "wss://example.invalid",
    )
}
