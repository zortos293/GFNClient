package com.opencloudgaming.opennow

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.PeerConnection

class StreamSignalingFailureTest {
    @Test
    fun staleSignalingEndpointRequestsSessionRecovery() {
        assertEquals(
            SignalingFailureDisposition.RecoverSession,
            signalingFailureDisposition("Expected HTTP 101 response but was '404 Not Found' http=404"),
        )
    }

    @Test
    fun goneSessionAndNormalServerCloseRemainTerminal() {
        assertEquals(
            SignalingFailureDisposition.SessionEnded,
            signalingFailureDisposition("http=410 Gone"),
        )
        assertEquals(
            SignalingFailureDisposition.SessionEnded,
            signalingFailureDisposition("code=1000", normalClosureMeansSessionEnded = true),
        )
    }

    @Test
    fun transientSignalingFailureRetriesTransport() {
        assertEquals(
            SignalingFailureDisposition.RetryTransport,
            signalingFailureDisposition("socket timeout"),
        )
    }

    @Test
    fun serverHeartbeatGetsImmediateProtocolReply() {
        assertEquals(
            """{"hb":1}""",
            signalingHeartbeatReply(buildJsonObject { put("hb", 1) }),
        )
        assertNull(signalingHeartbeatReply(buildJsonObject { put("ack", 1) }))
    }

    @Test
    fun activeIceTransportSurvivesTransientSignalingFailure() {
        val transient = SignalingFailureDisposition.RetryTransport

        assertTrue(shouldPreserveMediaAfterSignalingFailure(transient, PeerConnection.IceConnectionState.CHECKING))
        assertTrue(shouldPreserveMediaAfterSignalingFailure(transient, PeerConnection.IceConnectionState.CONNECTED))
        assertTrue(shouldPreserveMediaAfterSignalingFailure(transient, PeerConnection.IceConnectionState.COMPLETED))
        assertFalse(shouldPreserveMediaAfterSignalingFailure(transient, PeerConnection.IceConnectionState.DISCONNECTED))
        assertFalse(shouldPreserveMediaAfterSignalingFailure(transient, PeerConnection.IceConnectionState.FAILED))
        assertFalse(shouldPreserveMediaAfterSignalingFailure(transient, null))
    }

    @Test
    fun terminalAndStaleSignalingFailuresStillTakeTheirNormalRecoveryPaths() {
        assertFalse(
            shouldPreserveMediaAfterSignalingFailure(
                SignalingFailureDisposition.SessionEnded,
                PeerConnection.IceConnectionState.CONNECTED,
            ),
        )
        assertFalse(
            shouldPreserveMediaAfterSignalingFailure(
                SignalingFailureDisposition.RecoverSession,
                PeerConnection.IceConnectionState.CONNECTED,
            ),
        )
    }
}
