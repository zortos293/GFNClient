package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueLaunchStatusTest {
    @Test
    fun seatSetupStepFiveQueuePositionIsNotDisplayed() {
        val session = session(queuePosition = 1, seatSetupStep = 5)
        val state = OpenNowUiState(
            streamStatus = "queue",
            launchPhase = "Queue",
            queuePosition = 1,
            streamSession = session,
        )

        assertNull(queueDisplayPosition(session))
        assertNull(queueDisplayPosition(state))
        assertEquals("Starting session", queueLaunchStatusText(state))
    }

    @Test
    fun seatSetupStepOneQueuePositionIsDisplayed() {
        val session = session(queuePosition = 40, seatSetupStep = 1)
        val state = OpenNowUiState(
            streamStatus = "queue",
            streamSession = session,
        )

        assertEquals(40, queueDisplayPosition(session))
        assertEquals(40, queueDisplayPosition(state))
        assertEquals("Queue position 40", queueLaunchStatusText(state))
    }

    private fun session(
        queuePosition: Int?,
        seatSetupStep: Int?,
    ): SessionInfo =
        SessionInfo(
            sessionId = "session",
            status = 1,
            queuePosition = queuePosition,
            seatSetupStep = seatSetupStep,
            serverIp = "server",
            signalingServer = "server:443",
            signalingUrl = "wss://server:443/nvst/",
        )
}
