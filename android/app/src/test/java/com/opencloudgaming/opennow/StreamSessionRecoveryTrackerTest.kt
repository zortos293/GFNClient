package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSessionRecoveryTrackerTest {
    @Test
    fun repeatedRecoveryEscalatesOnlyForTheSameSession() {
        val tracker = StreamSessionRecoveryTracker()

        assertEquals(1, tracker.nextAttempt("session-a"))
        assertEquals(2, tracker.nextAttempt("session-a"))
        assertEquals(1, tracker.nextAttempt("session-b"))
        assertEquals(2, tracker.nextAttempt("session-b"))
    }

    @Test
    fun resetStartsRecoveryBudgetOver() {
        val tracker = StreamSessionRecoveryTracker()

        tracker.nextAttempt("session-a")
        tracker.reset()

        assertEquals(1, tracker.nextAttempt("session-a"))
    }

    @Test
    fun directSessionHostsAreNotReusedForSessionCreation() {
        assertTrue(isLikelyDirectSessionServerUrl("https://66.22.139.37"))
        assertTrue(isLikelyDirectSessionServerUrl("https://66-22-139-37.cloudmatchbeta.nvidiagrid.net"))
        assertFalse(isLikelyDirectSessionServerUrl("https://np-bom-01.cloudmatchbeta.nvidiagrid.net"))
        assertFalse(isLikelyDirectSessionServerUrl("https://prod.cloudmatchbeta.nvidiagrid.net"))
    }

    @Test
    fun statusSevenIsTerminalWhileCleanupStatusCanStillProgress() {
        assertFalse(isTerminalSessionStatus(0))
        assertFalse(isTerminalSessionStatus(1))
        assertFalse(isTerminalSessionStatus(2))
        assertFalse(isTerminalSessionStatus(3))
        assertFalse(isTerminalSessionStatus(6))
        assertTrue(isTerminalSessionStatus(4))
        assertTrue(isTerminalSessionStatus(5))
        assertTrue(isTerminalSessionStatus(7))
    }

    @Test
    fun exactSessionProbePreservesProviderTerminationCodesInDebugEvents() {
        val summary = recoverySessionProbeDebugSummary(
            GfnSessionDiagnosticResponse(
                operation = "session.recovery.probe",
                method = "GET",
                url = "https://streamer.example/v2/session/1234567890abcdef",
                statusCode = 200,
                requestBody = "",
                responseBody = """
                    {
                      "session": { "status": 7, "errorCode": 42 },
                      "requestStatus": {
                        "statusCode": 1,
                        "statusDescription": "SUCCESS_STATUS",
                        "unifiedErrorCode": -1970536448
                      }
                    }
                """.trimIndent(),
            ),
        )

        assertEquals(
            "Old session GET session=123456...cdef source=session.recovery.probe http=200 requestStatus=1 " +
                "description=SUCCESS_STATUS unifiedError=-1970536448 sessionStatus=7 sessionError=42",
            summary,
        )
    }

    @Test
    fun exactSessionProbeStillRecordsAnHttpFailureWithoutJson() {
        val summary = recoverySessionProbeDebugSummary(
            GfnSessionDiagnosticResponse(
                operation = "session.recovery.probe",
                method = "GET",
                url = "https://streamer.example/v2/session/session-a",
                statusCode = 404,
                requestBody = "",
                responseBody = "Not Found",
            ),
        )

        assertEquals(
            "Old session GET session=session-a source=session.recovery.probe http=404 requestStatus=unknown description=unknown " +
                "unifiedError=unknown sessionStatus=unknown sessionError=unknown",
            summary,
        )
    }
}
