package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAssignmentTest {
    @Test
    fun `uses standard zone from assigned session control host`() {
        assertEquals(
            "NP-WAW-01",
            assignedSessionZoneFromControlHost("np-waw-01.cloudmatchbeta.nvidiagrid.net"),
        )
    }

    @Test
    fun `uses alliance zone from assigned session control host`() {
        assertEquals(
            "NPA-TKC-IST-01",
            assignedSessionZoneFromControlHost("npa-tkc-ist-01.tkc.geforcenow.nvidiagrid.net"),
        )
    }

    @Test
    fun `ignores transport and untrusted hosts`() {
        assertNull(assignedSessionZoneFromControlHost("85-29-33-38.tkc.geforcenow.nvidiagrid.net"))
        assertNull(assignedSessionZoneFromControlHost("np-waw-01.example.com"))
        assertNull(assignedSessionZoneFromControlHost(null))
    }

    @Test
    fun `reported server prefers assignment over request zone`() {
        val session = SessionInfo(
            sessionId = "session-1",
            status = 2,
            zone = "NP-LAX-03",
            assignedZone = "NP-PDX-01",
            serverIp = "203.0.113.10",
            signalingServer = "203.0.113.10:443",
            signalingUrl = "wss://203.0.113.10:443/nvst/",
        )

        assertEquals("NP-PDX-01", session.reportedServerZone())
        assertEquals("NP-LAX-03", session.copy(assignedZone = null).reportedServerZone())
    }

    @Test
    fun `ready session update preserves an earlier assignment when provider omits it`() {
        val previous = session(status = 1, assignedZone = "NP-PDX-01")
        val readyWithoutAssignment = session(status = 2, assignedZone = null)

        assertEquals(
            "NP-PDX-01",
            mergeQueueSessionState(previous, readyWithoutAssignment).assignedZone,
        )
    }

    private fun session(status: Int, assignedZone: String?): SessionInfo = SessionInfo(
        sessionId = "session-1",
        status = status,
        zone = "NP-LAX-03",
        assignedZone = assignedZone,
        serverIp = "203.0.113.10",
        signalingServer = "203.0.113.10:443",
        signalingUrl = "wss://203.0.113.10:443/nvst/",
    )
}
