package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStreamReadinessTest {
    @Test
    fun readyCloudSessionDoesNotStartNativeTransportBeforeClaimCompletes() {
        val readySession = readySession()

        assertFalse(
            OpenNowUiState(
                streamStatus = "queue",
                streamSession = readySession,
            ).isNativeStreamReady(),
        )
        assertTrue(
            OpenNowUiState(
                streamStatus = "connecting",
                streamSession = readySession,
            ).isNativeStreamReady(),
        )
    }

    @Test
    fun connectingStateStillRequiresAStreamReadySession() {
        assertFalse(
            OpenNowUiState(
                streamStatus = "connecting",
                streamSession = readySession().copy(status = 1),
            ).isNativeStreamReady(),
        )
    }

    private fun readySession(): SessionInfo = SessionInfo(
        sessionId = "session-id",
        status = 2,
        serverIp = "stream.example.test",
        signalingServer = "stream.example.test:443",
        signalingUrl = "wss://stream.example.test/nvst/",
    )
}
