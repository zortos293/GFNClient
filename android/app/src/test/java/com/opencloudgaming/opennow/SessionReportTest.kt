package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionReportTest {
    @Test
    fun healthySessionScoresOneHundred() {
        assertEquals(
            100,
            sessionQualityScore(
                averagePingMs = 25,
                packetLossPct = 0.05,
                averageJitterMs = 3.0,
                averageFps = 60.0,
                targetFps = 60,
                averageDecodeMs = 4.0,
            ),
        )
        assertEquals(SessionReportRating.Excellent, sessionReportRating(100))
    }

    @Test
    fun poorNetworkProducesLowScore() {
        val score = sessionQualityScore(
            averagePingMs = 210,
            packetLossPct = 6.0,
            averageJitterMs = 55.0,
            averageFps = 35.0,
            targetFps = 60,
            averageDecodeMs = 28.0,
        )

        assertTrue(score < 20)
        assertEquals(SessionReportRating.Poor, sessionReportRating(score))
    }

    @Test
    fun accumulatorUsesPacketDeltasAndAddsContextualWifiAdvice() {
        val settings = StreamSettings(
            resolution = "1920x1080",
            fps = 60,
            maxBitrateMbps = 50,
            codec = VideoCodec.H264,
            colorQuality = ColorQuality.EightBit420,
        )
        val accumulator = StreamSessionReportAccumulator(
            launchProfile = StreamReportLaunchProfile(
                gameTitle = "Test Game",
                selectedSettings = settings,
                eligibleSettings = settings,
                initialSettings = settings,
            ),
            startedAtMs = 1_000L,
        )

        repeat(10) {
            accumulator.record(
                stats = StreamRuntimeStats(
                    bitrateKbps = 28_000,
                    pingMs = 95,
                    fps = 58,
                    resolution = "1920x1080",
                    codec = "H264",
                    decodeMs = 5.0,
                    jitterMs = 22.0,
                    packetLossPct = 99.0,
                    packetsLostDelta = 2,
                    packetsReceivedDelta = 98,
                ),
                network = AndroidRuntimeDiagnosticsSnapshot(
                    networkKind = AndroidNetworkKind.Wifi,
                    networkSignalBars = 3,
                    networkDownstreamKbps = 65_000,
                    wifiFrequencyMhz = 2_437,
                    wifiBand = AndroidWifiBand.TwoPointFourGhz,
                ),
            )
        }

        val report = accumulator.finish(11_000L)
        assertNotNull(report)
        report!!
        assertEquals(10, report.sampleCount)
        assertFalse(report.limitedData)
        assertEquals(95, report.averagePingMs)
        assertEquals(28_000, report.averageBitrateKbps)
        assertEquals(2.0, report.packetLossPct ?: -1.0, 0.001)
        assertEquals(AndroidWifiBand.TwoPointFourGhz, report.wifiBand)
        assertTrue(report.recommendations.any { it.title == "Use 5 GHz or 6 GHz Wi-Fi" })
        assertTrue(report.recommendations.any { it.title == "Reduce packet loss" })
    }

    @Test
    fun reportExplainsDeviceServerAndRecoveryDowngrades() {
        val selected = StreamSettings(
            resolution = "3840x2160",
            fps = 120,
            maxBitrateMbps = 75,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
        )
        val eligible = selected.copy(resolution = "2560x1440", fps = 60, hdrEnabled = false)
        val initial = eligible.copy(maxBitrateMbps = 35)
        val safe = initial.copy(codec = VideoCodec.H264, colorQuality = ColorQuality.EightBit420)
        val accumulator = StreamSessionReportAccumulator(
            launchProfile = StreamReportLaunchProfile(
                gameTitle = "Test Game",
                selectedSettings = selected,
                eligibleSettings = eligible,
                initialSettings = initial,
            ),
            startedAtMs = 0L,
        )
        accumulator.recordRecovery("H265 did not render a first frame", safe)
        accumulator.recordActiveMode(
            ActiveStreamModeStatus(
                requestedResolution = "2560x1440",
                displayedResolution = "1920x1080",
                serverNegotiatedResolution = "1920x1080",
                resolutionSource = StreamResolutionChangeSource.ServerNegotiatedFallback,
                safeVideoRecoveryActive = true,
                requestedProfile = initial.toActiveStreamTransportProfile(),
                transportProfile = safe.toActiveStreamTransportProfile(),
            ),
        )
        accumulator.record(
            StreamRuntimeStats(
                bitrateKbps = 20_000,
                pingMs = 30,
                fps = 60,
                resolution = "1920x1080",
                codec = "H264",
                decodeMs = 4.0,
                jitterMs = 3.0,
                packetLossPct = 0.0,
            ),
        )

        val report = accumulator.finish(5_000L)
        assertNotNull(report)
        report!!
        assertTrue(report.downgrades.any { it.title == "Account or session limit" })
        assertTrue(report.downgrades.any { it.title == "Device compatibility adjustment" })
        assertTrue(report.downgrades.any { it.title == "Safe video recovery" })
        assertTrue(report.downgrades.any { it.title == "Delivered resolution changed" })
    }

    @Test
    fun wifiFrequencyIsClassifiedWithoutGuessing() {
        assertEquals(AndroidWifiBand.TwoPointFourGhz, androidWifiBandForFrequency(2_412))
        assertEquals(AndroidWifiBand.FiveGhz, androidWifiBandForFrequency(5_220))
        assertEquals(AndroidWifiBand.SixGhz, androidWifiBandForFrequency(6_115))
        assertEquals(AndroidWifiBand.Unknown, androidWifiBandForFrequency(null))
    }
}
