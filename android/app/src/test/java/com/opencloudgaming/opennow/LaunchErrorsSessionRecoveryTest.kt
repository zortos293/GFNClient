package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchErrorsSessionRecoveryTest {
    @Test
    fun terminalSessionDoesNotPromiseAnAutomaticReplacementQueue() {
        val message = normalizeLaunchErrorMessage(
            TerminalSessionStatusException(status = 7, latestSession = null),
        )

        assertEquals(
            "The cloud provider ended this session (status 7). " +
                "OpenNOW did not stop it or start a replacement queue.",
            message,
        )
    }
}
