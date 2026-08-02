package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamStatusBatteryTest {

    @Test
    fun unknownBatteryUsesTheUnknownIcon() {
        assertEquals(StreamBatteryLevel.Unknown, streamBatteryLevel(null))
    }

    @Test
    fun batteryIconFillTracksTheReportedPercentage() {
        val expected = listOf(
            0 to StreamBatteryLevel.Empty,
            10 to StreamBatteryLevel.One,
            25 to StreamBatteryLevel.Two,
            40 to StreamBatteryLevel.Three,
            55 to StreamBatteryLevel.Four,
            70 to StreamBatteryLevel.Five,
            90 to StreamBatteryLevel.Six,
            100 to StreamBatteryLevel.Full,
        )

        expected.forEach { (percent, level) ->
            assertEquals("battery at $percent%", level, streamBatteryLevel(percent))
        }
    }

    @Test
    fun outOfRangeReadingsAreClamped() {
        assertEquals(StreamBatteryLevel.Empty, streamBatteryLevel(-10))
        assertEquals(StreamBatteryLevel.Full, streamBatteryLevel(150))
    }
}
