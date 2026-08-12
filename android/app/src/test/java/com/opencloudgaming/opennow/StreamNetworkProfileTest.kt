package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamNetworkProfileTest {
    private val requested = StreamSettings(maxBitrateMbps = 39)

    @Test
    fun capsTwoPointFourGhzProfileWithTwentyPercentLinkHeadroom() {
        val adjusted = requested.adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Wifi,
                wifiBand = AndroidWifiBand.TwoPointFourGhz,
                networkDownstreamKbps = 22_863,
            ),
        )

        assertEquals(19, adjusted.maxBitrateMbps)
    }

    @Test
    fun keepsAlreadySafeTwoPointFourGhzProfile() {
        val adjusted = requested.copy(maxBitrateMbps = 18).adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Wifi,
                wifiBand = AndroidWifiBand.TwoPointFourGhz,
                networkDownstreamKbps = 22_863,
            ),
        )

        assertEquals(18, adjusted.maxBitrateMbps)
    }

    @Test
    fun doesNotCapFasterWifiBandsEthernetOrUnknownWifi() {
        val fiveGhz = requested.adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Wifi,
                wifiBand = AndroidWifiBand.FiveGhz,
                networkDownstreamKbps = 22_863,
            ),
        )
        val ethernet = requested.adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Ethernet,
                networkDownstreamKbps = 22_863,
            ),
        )
        val unknownWifiBand = requested.adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Wifi,
                wifiBand = AndroidWifiBand.Unknown,
                networkDownstreamKbps = 22_863,
            ),
        )

        assertEquals(requested, fiveGhz)
        assertEquals(requested, ethernet)
        assertEquals(requested, unknownWifiBand)
    }

    @Test
    fun doesNotCapWithoutAUsableLinkEstimate() {
        val adjusted = requested.adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Wifi,
                wifiBand = AndroidWifiBand.TwoPointFourGhz,
                networkDownstreamKbps = null,
            ),
        )

        assertEquals(requested, adjusted)
    }

    @Test
    fun neverProducesAnInvalidZeroBitrate() {
        val adjusted = requested.adjustedForCurrentNetwork(
            AndroidRuntimeDiagnosticsSnapshot(
                networkKind = AndroidNetworkKind.Wifi,
                wifiBand = AndroidWifiBand.TwoPointFourGhz,
                networkDownstreamKbps = 500,
            ),
        )

        assertEquals(1, adjusted.maxBitrateMbps)
    }
}
