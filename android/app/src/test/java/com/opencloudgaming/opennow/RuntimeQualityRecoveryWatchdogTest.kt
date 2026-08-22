package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeQualityRecoveryWatchdogTest {
    @Test
    fun transientPacketLossDoesNotRestartStream() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 3)
        val degraded = networkStats(lossPct = 20.0)

        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(networkStats(lossPct = 1.0), requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
    }

    @Test
    fun sustainedPacketLossRequestsNetworkRecovery() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 3)
        val degraded = networkStats(lossPct = 20.0)

        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertEquals(
            RuntimeQualityRecoveryReason.NetworkDegraded,
            watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true),
        )
    }

    @Test
    fun sustainedDecoderDeficitRequestsDecoderRecovery() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 3)
        val overloaded = StreamRuntimeStats(
            receivedFps = 60,
            decodedFps = 30,
            decodeMs = 24.0,
            packetsLostDelta = 0,
            packetsReceivedDelta = 1_000,
            packetLossPct = 0.0,
        )

        assertNull(watchdog.observe(overloaded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(overloaded, requestedFps = 60, recoveryEligible = true))
        assertEquals(
            RuntimeQualityRecoveryReason.DecoderOverloaded,
            watchdog.observe(overloaded, requestedFps = 60, recoveryEligible = true),
        )
    }

    @Test
    fun packetLossTakesPrecedenceOverDecoderDeficit() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 1)
        val degraded = networkStats(lossPct = 20.0).copy(
            receivedFps = 60,
            decodedFps = 20,
            decodeMs = 30.0,
        )

        assertEquals(
            RuntimeQualityRecoveryReason.NetworkDegraded,
            watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true),
        )
    }

    @Test
    fun networkRecoveryKeepsCodecAndCapsBandwidthAndFps() {
        val recovered = StreamSettings(
            resolution = "2560x1440",
            fps = 120,
            maxBitrateMbps = 75,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
            enableCloudGsync = true,
            streamSharpeningEnabled = true,
        ).runtimeQualityRecoveryProfile(RuntimeQualityRecoveryReason.NetworkDegraded)

        assertEquals("2560x1440", recovered.resolution)
        assertEquals(VideoCodec.H265, recovered.codec)
        assertEquals(30, recovered.fps)
        assertEquals(12, recovered.maxBitrateMbps)
        assertEquals(ColorQuality.EightBit420, recovered.colorQuality)
        assertEquals(false, recovered.hdrEnabled)
        assertEquals(false, recovered.enableCloudGsync)
        assertEquals(false, recovered.streamSharpeningEnabled)
    }

    @Test
    fun decoderRecoveryUsesThirtyFpsH264WithoutChangingGeometry() {
        val recovered = StreamSettings(
            resolution = "1680x720",
            aspectRatio = "21:9",
            fps = 60,
            maxBitrateMbps = 75,
            codec = VideoCodec.H265,
        ).runtimeQualityRecoveryProfile(RuntimeQualityRecoveryReason.DecoderOverloaded)

        assertEquals("1680x720", recovered.resolution)
        assertEquals("21:9", recovered.aspectRatio)
        assertEquals(VideoCodec.H264, recovered.codec)
        assertEquals(30, recovered.fps)
        assertEquals(25, recovered.maxBitrateMbps)
    }

    private fun networkStats(lossPct: Double): StreamRuntimeStats = StreamRuntimeStats(
        packetLossPct = lossPct,
        packetsLostDelta = 100,
        packetsReceivedDelta = 900,
    )
}
