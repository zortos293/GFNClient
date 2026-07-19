package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
