package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputDiagnosticsTest {
    @Test
    fun retainedControllerStateSurvivesRecentEventOverflow() {
        var now = 100L
        val buffer = InputDiagnosticsBuffer(
            maxRecentLines = 2,
            maxRetainedLines = 4,
            elapsedRealtime = { now++ },
        )

        buffer.addRetained("controller.axes.0", "physical gamepad axes x=1.000 y=0.000")
        buffer.add("touch event one")
        buffer.add("touch event two")
        buffer.add("touch event three")

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.contains("input.state:"))
        assertTrue(snapshot.contains("controller.axes.0 100 physical gamepad axes x=1.000 y=0.000"))
        assertFalse(snapshot.contains("touch event one"))
        assertTrue(snapshot.contains("touch event two"))
        assertTrue(snapshot.contains("touch event three"))
    }

    @Test
    fun throttledStateSkipsFormattingUntilIntervalExpires() {
        var now = 1_000L
        var formatted = 0
        val buffer = InputDiagnosticsBuffer(
            maxRecentLines = 2,
            maxRetainedLines = 2,
            elapsedRealtime = { now },
        )

        buffer.retainThrottled("controller.packet.0", 1_000L) {
            formatted += 1
            "packet first"
        }
        now = 1_500L
        buffer.retainThrottled("controller.packet.0", 1_000L) {
            formatted += 1
            "packet suppressed"
        }
        now = 2_000L
        buffer.retainThrottled("controller.packet.0", 1_000L) {
            formatted += 1
            "packet latest"
        }

        assertEquals(2, formatted)
        val snapshot = buffer.snapshot()
        assertFalse(snapshot.contains("packet first"))
        assertFalse(snapshot.contains("packet suppressed"))
        assertTrue(snapshot.contains("controller.packet.0 2000 packet latest"))
    }

    @Test
    fun retainedStateRemainsBounded() {
        var now = 10L
        val buffer = InputDiagnosticsBuffer(
            maxRecentLines = 1,
            maxRetainedLines = 2,
            elapsedRealtime = { now++ },
        )

        buffer.retain("oldest", "one")
        buffer.retain("middle", "two")
        buffer.retain("newest", "three")

        val snapshot = buffer.snapshot()
        assertFalse(snapshot.contains("oldest"))
        assertTrue(snapshot.contains("middle"))
        assertTrue(snapshot.contains("newest"))
    }

    @Test
    fun countedStateAggregatesRepetitiveEventsWithoutUsingRecentCapacity() {
        var now = 50L
        val buffer = InputDiagnosticsBuffer(
            maxRecentLines = 1,
            maxRetainedLines = 2,
            elapsedRealtime = { now++ },
        )

        repeat(3) {
            buffer.retainCounted("touch-route.activity") { "touch consumed by view" }
        }

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.contains("touch-route.activity 52 count=3 touch consumed by view"))
        assertFalse(snapshot.contains("input.diagnostics:"))
    }

    @Test
    fun resultStateRetainsLatestOutcomeAndLastSuccessAndFailure() {
        var now = 100L
        val buffer = InputDiagnosticsBuffer(
            maxRecentLines = 1,
            maxRetainedLines = 4,
            elapsedRealtime = { now },
        )

        buffer.retainResult("heartbeat.input", succeeded = false) { "path=worker" }
        now = 200L
        buffer.retainResult("heartbeat.input", succeeded = true) { "path=worker" }

        val snapshot = buffer.snapshot()
        assertTrue(snapshot.contains("heartbeat.input.last 200 count=2 success=true path=worker"))
        assertTrue(snapshot.contains("heartbeat.input.failure 100 count=1 path=worker"))
        assertTrue(snapshot.contains("heartbeat.input.success 200 count=1 path=worker"))
        assertFalse(snapshot.contains("input.diagnostics:"))
    }
}
