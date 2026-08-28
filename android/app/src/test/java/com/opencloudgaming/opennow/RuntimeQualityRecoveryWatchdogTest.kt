package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeQualityRecoveryWatchdogTest {
    @Test
    fun misleadingSmoothedLossWithoutRawLossDoesNotRecover() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 2)
        val misleading = networkStats(lost = 0, received = 500, displayedLossPct = 35.0)

        assertNull(watchdog.observe(misleading, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(misleading, requestedFps = 60, recoveryEligible = true))
    }

    @Test
    fun capturedDisplayedSpikeDoesNotOverrideMildRawLoss() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 3)
        val captured = networkStats(lost = 2, received = 141, displayedLossPct = 11.221449851042701)

        repeat(6) {
            assertNull(watchdog.observe(captured, requestedFps = 60, recoveryEligible = true))
        }
    }

    @Test
    fun transientRawPacketLossDoesNotRecover() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 3)
        val degraded = networkStats(lost = 60, received = 940)

        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(networkStats(lost = 0, received = 1_000), requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
    }

    @Test
    fun sustainedRawPacketLossRequestsNetworkRecovery() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 3)
        val degraded = networkStats(lost = 60, received = 940, displayedLossPct = 0.0)

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
            decodedFps = 56,
            decodeMs = 18.0,
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
    fun ineligibleSampleResetsAccumulatedEvidence() {
        val watchdog = RuntimeQualityRecoveryWatchdog(samplesBeforeRecovery = 2)
        val degraded = networkStats(lost = 60, received = 940)

        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = false))
        assertNull(watchdog.observe(degraded, requestedFps = 60, recoveryEligible = true))
    }

    @Test
    fun recoveryProfilesPreserveGeometryAndReduceLoad() {
        val requested = StreamSettings(
            resolution = "2560x1440",
            aspectRatio = "16:9",
            fps = 120,
            maxBitrateMbps = 75,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
            streamSharpeningEnabled = true,
        )

        val network = requested.runtimeQualityRecoveryProfile(RuntimeQualityRecoveryReason.NetworkDegraded)
        assertEquals("2560x1440", network.resolution)
        assertEquals(VideoCodec.H265, network.codec)
        assertEquals(30, network.fps)
        assertEquals(12, network.maxBitrateMbps)
        assertEquals(false, network.hdrEnabled)

        val decoder = requested.runtimeQualityRecoveryProfile(RuntimeQualityRecoveryReason.DecoderOverloaded)
        assertEquals("2560x1440", decoder.resolution)
        assertEquals(VideoCodec.H264, decoder.codec)
        assertEquals(30, decoder.fps)
        assertEquals(25, decoder.maxBitrateMbps)
    }

    private fun networkStats(
        lost: Long,
        received: Long,
        displayedLossPct: Double = lost.toDouble() / (lost + received).toDouble() * 100.0,
    ): StreamRuntimeStats = StreamRuntimeStats(
        packetLossPct = displayedLossPct,
        packetsLostDelta = lost,
        packetsReceivedDelta = received,
    )
}
