package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialStreamConnectionStatusTest {
    @Test
    fun translatesInitialNativeStatesIntoUserFacingCopy() {
        assertEquals("Preparing your stream", initialStreamConnectionStatus("Preparing").title)
        assertEquals("Connecting to your game", initialStreamConnectionStatus("Connecting signaling").title)
        assertEquals("Starting the video stream", initialStreamConnectionStatus("Waiting for offer").title)
        assertEquals("Almost ready", initialStreamConnectionStatus("ICE_CHECKING").title)
        assertEquals("Connection established", initialStreamConnectionStatus("Streaming").title)
    }

    @Test
    fun explainsAnInitialRetryWithoutTechnicalErrorText() {
        val retry = initialStreamConnectionStatus("Reconnecting stream (1/3)")

        assertEquals("Retrying connection", retry.phase)
        assertEquals("Connecting again", retry.title)
        assertEquals(
            "The initial connection did not finish, so OpenNOW is retrying it.",
            retry.detail,
        )
    }

    @Test
    fun explainsSafeVideoFallback() {
        val fallback = initialStreamConnectionStatus(
            "Video packets arrived but no frame rendered. Restarting with safe H264 profile.",
        )

        assertEquals("Optimizing video", fallback.phase)
        assertEquals("Trying a compatible video mode", fallback.title)
    }
}
