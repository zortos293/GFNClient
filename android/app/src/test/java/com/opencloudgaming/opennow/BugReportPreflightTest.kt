package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportPreflightTest {
    private val settings = StreamSettings(
        resolution = "1920x1080",
        aspectRatio = "16:9",
        fps = 60,
        maxBitrateMbps = 35,
        codec = VideoCodec.H264,
    )

    @Test
    fun healthySixGhzDoesNotSuggestChangingWifiBand() {
        val deck = buildBugReportPreflightDeck(
            BugReportPreflightEvidence(
                requestedSettings = settings,
                runtimeStats = StreamRuntimeStats(
                    bitrateKbps = 28_000,
                    availableIncomingBitrateKbps = 74_000,
                    pingMs = 24,
                    fps = 60,
                    resolution = "1920x1080",
                    codec = "H264",
                    jitterMs = 3.0,
                    packetLossPct = 0.05,
                ),
                runtimeDiagnostics = AndroidRuntimeDiagnosticsSnapshot(
                    thermalStatus = AndroidThermalStatus.None,
                    networkKind = AndroidNetworkKind.Wifi,
                    networkSignalBars = 4,
                    networkDownstreamKbps = 200_000,
                    wifiFrequencyMhz = 6_115,
                    wifiBand = AndroidWifiBand.SixGhz,
                ),
            ),
        )

        val connection = deck.cards.first()
        assertEquals(BugReportPreflightTone.Healthy, connection.tone)
        assertTrue(connection.facts.contains("6 GHz"))
        assertTrue(connection.recommendations.isEmpty())
        assertFalse(connection.toString().contains("Use 5 GHz"))
        assertFalse(connection.toString().contains("2.4 GHz Wi-Fi"))
        val video = deck.cards[1]
        assertTrue(video.facts.contains("Requested max 35 Mbps"))
        assertTrue(video.facts.contains("28 Mbps video"))
        assertTrue(video.facts.contains("74 Mbps WebRTC receive estimate"))
    }

    @Test
    fun runtimeBitrateStatusShowsActualAndRequestedMaximum() {
        assertEquals("28.4 Mbps / 35 Mbps max", formatRuntimeBitrateStatus(28_400, 35))
        assertEquals("-- / 35 Mbps max", formatRuntimeBitrateStatus(null, 35))
    }

    @Test
    fun degradedTwoPointFourGhzShowsOnlyMatchedNetworkActions() {
        val deck = buildBugReportPreflightDeck(
            BugReportPreflightEvidence(
                requestedSettings = settings,
                runtimeStats = StreamRuntimeStats(
                    bitrateKbps = 2_000,
                    pingMs = 155,
                    fps = 42,
                    resolution = "1280x720",
                    codec = "H264",
                    jitterMs = 28.0,
                    packetLossPct = 2.2,
                ),
                runtimeDiagnostics = AndroidRuntimeDiagnosticsSnapshot(
                    thermalStatus = AndroidThermalStatus.None,
                    networkKind = AndroidNetworkKind.Wifi,
                    networkSignalBars = 2,
                    networkDownstreamKbps = 12_000,
                    wifiFrequencyMhz = 2_412,
                    wifiBand = AndroidWifiBand.TwoPointFourGhz,
                ),
            ),
        )

        val connection = deck.cards.first()
        val titles = connection.recommendations.map { it.title }
        assertEquals(BugReportPreflightTone.Warning, connection.tone)
        assertTrue(titles.contains("Use 5 GHz or 6 GHz Wi-Fi"))
        assertTrue(titles.contains("Reduce packet loss"))
        assertTrue(titles.contains("Stabilize latency"))
        assertTrue(titles.contains("Lower the maximum bitrate"))
    }

    @Test
    fun ethernetSessionNeverGetsWifiAdvice() {
        val deck = buildBugReportPreflightDeck(
            BugReportPreflightEvidence(
                requestedSettings = settings,
                runtimeStats = StreamRuntimeStats(
                    bitrateKbps = 30_000,
                    pingMs = 18,
                    fps = 60,
                    packetLossPct = 0.0,
                ),
                runtimeDiagnostics = AndroidRuntimeDiagnosticsSnapshot(
                    networkKind = AndroidNetworkKind.Ethernet,
                    networkDownstreamKbps = 500_000,
                ),
            ),
        )

        val connection = deck.cards.first()
        assertEquals(BugReportPreflightTone.Healthy, connection.tone)
        assertTrue(connection.facts.contains("LAN"))
        assertTrue(connection.recommendations.isEmpty())
        assertFalse(connection.toString().contains("Wi-Fi"))
    }

    @Test
    fun videoAdviceMatchesActualDecoderAndThermalState() {
        val deck = buildBugReportPreflightDeck(
            BugReportPreflightEvidence(
                requestedSettings = settings.copy(codec = VideoCodec.H265),
                runtimeStats = StreamRuntimeStats(
                    bitrateKbps = 18_000,
                    pingMs = 28,
                    fps = 30,
                    resolution = "1920x1080",
                    codec = "H265",
                    decodeMs = 15.5,
                    packetLossPct = 0.0,
                ),
                runtimeDiagnostics = AndroidRuntimeDiagnosticsSnapshot(
                    thermalStatus = AndroidThermalStatus.Severe,
                    networkKind = AndroidNetworkKind.Ethernet,
                    networkDownstreamKbps = 500_000,
                ),
                codecReport = RuntimeCodecReport(
                    capabilities = listOf(
                        CodecCapability(
                            codec = VideoCodec.H265,
                            decoderAvailable = true,
                            encoderAvailable = false,
                            hardwareDecoder = false,
                            hardwareEncoder = false,
                            webRtcDecoderAvailable = true,
                            webRtcHardwareDecoderAvailable = false,
                        ),
                        CodecCapability(
                            codec = VideoCodec.H264,
                            decoderAvailable = true,
                            encoderAvailable = false,
                            hardwareDecoder = true,
                            hardwareEncoder = false,
                            webRtcDecoderAvailable = true,
                            webRtcHardwareDecoderAvailable = true,
                        ),
                    ),
                    nativeRuntimeSummary = "test",
                    androidTvProfile = false,
                    lowPowerGpuProfile = false,
                ),
            ),
        )

        val video = deck.cards[1]
        val titles = video.recommendations.map { it.title }
        assertEquals(BugReportPreflightTone.Warning, video.tone)
        assertTrue(video.facts.contains("Software decoder"))
        assertTrue(video.facts.contains("Thermal severe"))
        assertTrue(titles.contains("Reduce device decode load"))
        assertTrue(titles.contains("Let the device cool down"))
        assertTrue(titles.contains("Use a hardware-decoded codec"))
        assertTrue(video.recommendations.any { it.detail.contains("Try H264") })
    }

    @Test
    fun inputCardDistinguishesCapturedMouseFromMissingEvidence() {
        val missing = buildBugReportPreflightDeck(
            BugReportPreflightEvidence(requestedSettings = settings),
        ).cards.last()
        assertEquals(BugReportPreflightTone.Notice, missing.tone)
        assertEquals("For an input problem, reproduce it once", missing.recommendations.single().title)

        val captured = buildBugReportPreflightDeck(
            BugReportPreflightEvidence(
                requestedSettings = settings,
                inputDiagnostics = """
                    input channel open label=input_channel_v1
                    input channel open label=input_channel_partially_reliable
                    external mouse move sent source=131076 device=7 mode=relative
                """.trimIndent(),
            ),
        ).cards.last()
        assertEquals(BugReportPreflightTone.Healthy, captured.tone)
        assertTrue(captured.facts.any { it.contains("External mouse") })
        assertTrue(captured.facts.contains("Input channels opened"))
        assertTrue(captured.facts.contains("Mouse movement sent"))
        assertTrue(captured.recommendations.isEmpty())
    }
}
