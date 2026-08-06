package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NativeStreamTransportIdentityTest {
    @Test
    fun runtimeSessionSnapshotDoesNotChangeTransportIdentity() {
        val initial = session()
        val refreshed = initial.copy(
            status = 3,
            queuePosition = 0,
            seatSetupStep = 4,
            negotiatedStreamProfile = NegotiatedStreamProfile(
                resolution = "1680x720",
                fps = 120,
                codec = VideoCodec.H265,
            ),
            monitorSnapshot = SessionMonitorSnapshot(
                requestedResolution = "1376x640",
                requestedFps = 120,
                returnedResolution = "1680x720",
                returnedFps = 120,
            ),
            requestedStreamingFeatures = StreamingFeatures(bitDepth = 0),
            finalizedStreamingFeatures = StreamingFeatures(bitDepth = 0),
        )

        assertEquals(
            initial.nativeStreamTransportIdentity(),
            refreshed.nativeStreamTransportIdentity(),
        )
    }

    @Test
    fun endpointChangeDoesChangeTransportIdentity() {
        val initial = session()
        val moved = initial.copy(
            serverIp = "203.0.113.11",
            signalingServer = "new.example.test",
            signalingUrl = "wss://new.example.test/nvst/sign_in",
            mediaConnectionInfo = MediaConnectionInfo("203.0.113.11", 5005),
        )

        assertNotEquals(
            initial.nativeStreamTransportIdentity(),
            moved.nativeStreamTransportIdentity(),
        )
    }

    private fun session(): SessionInfo = SessionInfo(
        sessionId = "session-1",
        status = 2,
        serverIp = "203.0.113.10",
        signalingServer = "stream.example.test",
        signalingUrl = "wss://stream.example.test/nvst/sign_in",
        iceServers = listOf(IceServer(listOf("stun:stun.example.test:3478"))),
        mediaConnectionInfo = MediaConnectionInfo("203.0.113.10", 5004),
    )
}
