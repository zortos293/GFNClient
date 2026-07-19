package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimerAnchorStoreTest {
    @Test
    fun reconnectKeepsPersistedStartForSameSession() {
        assertEquals(
            1_000L,
            resolveSessionTimerStartedAtMs(
                sessionId = "session-a",
                persistedSessionId = "session-a",
                persistedStartedAtMs = 1_000L,
                preferredStartedAtMs = null,
                nowMs = 2_000L,
            ),
        )
    }

    @Test
    fun newSessionUsesCurrentTimeInsteadOfPreviousSessionAnchor() {
        assertEquals(
            2_000L,
            resolveSessionTimerStartedAtMs(
                sessionId = "session-b",
                persistedSessionId = "session-a",
                persistedStartedAtMs = 1_000L,
                preferredStartedAtMs = null,
                nowMs = 2_000L,
            ),
        )
    }

    @Test
    fun recoveryCanCarryAnExistingInMemoryAnchor() {
        assertEquals(
            1_250L,
            resolveSessionTimerStartedAtMs(
                sessionId = "session-a",
                persistedSessionId = null,
                persistedStartedAtMs = 0L,
                preferredStartedAtMs = 1_250L,
                nowMs = 2_000L,
            ),
        )
    }
}
