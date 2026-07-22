package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLivenessWatchdogTest {
    @Test
    fun catastrophic1440pAv1FirstFrameRetriesH265OnlyOnce() {
        assertEquals(
            CatastrophicResolutionRecoveryStep.RetryWithH265,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.AV1,
                expectedResolution = "2560x1440",
                decodedResolution = "320x180",
                completedCodecFallbacks = 0,
            ),
        )
        assertEquals(
            CatastrophicResolutionRecoveryStep.None,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.AV1,
                expectedResolution = "2560x1440",
                decodedResolution = "320x180",
                completedCodecFallbacks = 1,
            ),
        )
    }

    @Test
    fun stable1440pH265DoesNotTriggerResolutionRecovery() {
        assertEquals(
            CatastrophicResolutionRecoveryStep.None,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.H265,
                expectedResolution = "2560x1440",
                decodedResolution = "2560x1440",
                completedCodecFallbacks = 0,
            ),
        )
    }

    @Test
    fun legitimatelyRequested320x180DoesNotTriggerResolutionRecovery() {
        assertEquals(
            CatastrophicResolutionRecoveryStep.None,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.AV1,
                expectedResolution = "320x180",
                decodedResolution = "320x180",
                completedCodecFallbacks = 0,
            ),
        )
    }

    @Test
    fun ordinaryProviderModeChangeDoesNotTriggerResolutionRecovery() {
        assertEquals(
            CatastrophicResolutionRecoveryStep.None,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.AV1,
                expectedResolution = "2560x1440",
                decodedResolution = "1920x1080",
                completedCodecFallbacks = 0,
            ),
        )
    }

    @Test
    fun failedH265RecoveryGetsOneFinalH264AttemptWithoutLooping() {
        assertEquals(
            CatastrophicResolutionRecoveryStep.RetryWithH264,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.H265,
                expectedResolution = "2560x1440",
                decodedResolution = "320x180",
                completedCodecFallbacks = 1,
            ),
        )
        assertEquals(
            CatastrophicResolutionRecoveryStep.None,
            catastrophicFirstDecodedResolutionRecoveryStep(
                transportCodec = VideoCodec.H264,
                expectedResolution = "2560x1440",
                decodedResolution = "320x180",
                completedCodecFallbacks = 2,
            ),
        )
    }

    @Test
    fun catastrophicCodecRecoveryPreservesRequested1440pGeometry() {
        val requested = StreamSettings(
            resolution = "2560x1440",
            aspectRatio = "16:9",
            fps = 60,
            codec = VideoCodec.AV1,
        )
        val h265 = requested.forCatastrophicResolutionRecovery(
            CatastrophicResolutionRecoveryStep.RetryWithH265,
        )
        val h264 = h265?.forCatastrophicResolutionRecovery(
            CatastrophicResolutionRecoveryStep.RetryWithH264,
        )

        assertEquals("2560x1440", h265?.resolution)
        assertEquals("16:9", h265?.aspectRatio)
        assertEquals(VideoCodec.H265, h265?.codec)
        assertEquals("2560x1440", h264?.resolution)
        assertEquals("16:9", h264?.aspectRatio)
        assertEquals(VideoCodec.H264, h264?.codec)
    }

    @Test
    fun advancedCodecRestartWaitsForDecoderReleaseAfterStablePlayback() {
        assertEquals(180L, advancedCodecRestartSettleDelayMs(VideoCodec.AV1, hadStableMedia = true))
        assertEquals(180L, advancedCodecRestartSettleDelayMs(VideoCodec.H265, hadStableMedia = true))
        assertEquals(0L, advancedCodecRestartSettleDelayMs(VideoCodec.H264, hadStableMedia = true))
        assertEquals(0L, advancedCodecRestartSettleDelayMs(VideoCodec.AV1, hadStableMedia = false))
    }

    @Test
    fun androidTvAllowsSlowHardwareDecoderStartupBeforeSafeFallback() {
        val tv = streamRecoveryTiming(androidTvProfile = true)
        val mobile = streamRecoveryTiming(androidTvProfile = false)

        assertEquals(5_000L, tv.keyframeAfterMs)
        assertEquals(2_500L, tv.keyframeIntervalMs)
        assertEquals(14_000L, tv.restartAfterMs)
        assertEquals(5_000L, mobile.keyframeAfterMs)
        assertEquals(2_500L, mobile.keyframeIntervalMs)
        assertEquals(10_000L, mobile.restartAfterMs)
        assertEquals(14_000L, firstVideoFrameRecoveryTimeoutMs(androidTvProfile = true))
        assertEquals(10_000L, firstVideoFrameRecoveryTimeoutMs(androidTvProfile = false))
    }

    @Test
    fun firstFrameRecoveryRetriesRequestedProfileThenAppliesSafeFallbackOnlyOnce() {
        assertEquals(
            FirstFrameRecoveryStep.RetryRequestedProfile,
            firstFrameRecoveryStep(
                transportHasStableMedia = false,
                reconnectAttempts = 0,
                safeVideoFallbackApplied = false,
            ),
        )
        assertEquals(
            FirstFrameRecoveryStep.ApplySafeVideoFallback,
            firstFrameRecoveryStep(
                transportHasStableMedia = false,
                reconnectAttempts = 1,
                safeVideoFallbackApplied = false,
            ),
        )
        assertEquals(
            FirstFrameRecoveryStep.ContinueBoundedTransportRecovery,
            firstFrameRecoveryStep(
                transportHasStableMedia = false,
                reconnectAttempts = 2,
                safeVideoFallbackApplied = true,
            ),
        )
    }

    @Test
    fun networkTransportRetriesPreserveTheRequestedCodec() {
        assertFalse(
            transportRestartShouldApplySafeVideoFallback(
                videoFailure = false,
                reconnectAttempts = 1,
                transportHasStableMedia = false,
            ),
        )
        assertTrue(
            transportRestartShouldApplySafeVideoFallback(
                videoFailure = true,
                reconnectAttempts = 1,
                transportHasStableMedia = false,
            ),
        )
    }

    @Test
    fun requestsKeyframesBeforeRestartingStalledMedia() {
        val watchdog = StreamLivenessWatchdog(
            keyframeAfterMs = 1_000L,
            keyframeIntervalMs = 500L,
            restartAfterMs = 3_000L,
        )

        watchdog.markConnected(0L)

        assertEquals(StreamLivenessAction.None, watchdog.observe(0L, bytesReceived = 10L, framesDecoded = 1L, connected = true))

        val first = watchdog.observe(1_000L, bytesReceived = 10L, framesDecoded = 1L, connected = true)
        assertTrue(first is StreamLivenessAction.RequestKeyframe)
        assertEquals(1, (first as StreamLivenessAction.RequestKeyframe).attempt)

        assertEquals(StreamLivenessAction.None, watchdog.observe(1_200L, bytesReceived = 10L, framesDecoded = 1L, connected = true))

        val second = watchdog.observe(1_500L, bytesReceived = 10L, framesDecoded = 1L, connected = true)
        assertTrue(second is StreamLivenessAction.RequestKeyframe)
        assertEquals(2, (second as StreamLivenessAction.RequestKeyframe).attempt)

        val restart = watchdog.observe(3_000L, bytesReceived = 10L, framesDecoded = 1L, connected = true)
        assertTrue(restart is StreamLivenessAction.RestartTransport)
    }

    @Test
    fun progressClearsPendingStallRecovery() {
        val watchdog = StreamLivenessWatchdog(
            keyframeAfterMs = 1_000L,
            keyframeIntervalMs = 500L,
            restartAfterMs = 3_000L,
        )

        watchdog.markConnected(0L)
        assertEquals(StreamLivenessAction.None, watchdog.observe(0L, bytesReceived = 10L, framesDecoded = 1L, connected = true))
        assertTrue(watchdog.observe(1_000L, bytesReceived = 10L, framesDecoded = 1L, connected = true) is StreamLivenessAction.RequestKeyframe)
        assertEquals(StreamLivenessAction.None, watchdog.observe(1_200L, bytesReceived = 11L, framesDecoded = 2L, connected = true))
        assertEquals(StreamLivenessAction.None, watchdog.observe(1_900L, bytesReceived = 11L, framesDecoded = 2L, connected = true))
    }

    @Test
    fun incomingBytesDoNotHideDecoderFrameStall() {
        val watchdog = StreamLivenessWatchdog(
            keyframeAfterMs = 1_000L,
            keyframeIntervalMs = 500L,
            restartAfterMs = 3_000L,
        )

        watchdog.markConnected(0L)
        assertEquals(StreamLivenessAction.None, watchdog.observe(100L, bytesReceived = 10L, framesDecoded = 0L, connected = true))
        assertEquals(StreamLivenessAction.None, watchdog.observe(900L, bytesReceived = 100L, framesDecoded = 0L, connected = true))

        val first = watchdog.observe(1_000L, bytesReceived = 200L, framesDecoded = 0L, connected = true)
        assertTrue(first is StreamLivenessAction.RequestKeyframe)
    }

    @Test
    fun fallsBackToBytesWhenFrameCounterIsMissing() {
        val watchdog = StreamLivenessWatchdog(
            keyframeAfterMs = 1_000L,
            keyframeIntervalMs = 500L,
            restartAfterMs = 3_000L,
        )

        watchdog.markConnected(0L)
        assertEquals(StreamLivenessAction.None, watchdog.observe(900L, bytesReceived = 10L, framesDecoded = null, connected = true))
        assertEquals(StreamLivenessAction.None, watchdog.observe(1_700L, bytesReceived = 20L, framesDecoded = null, connected = true))
        assertEquals(StreamLivenessAction.None, watchdog.observe(2_500L, bytesReceived = 30L, framesDecoded = null, connected = true))
    }

    @Test
    fun reportsMediaProgressSeparatelyFromTransportConnectivity() {
        val watchdog = StreamLivenessWatchdog(
            keyframeAfterMs = 1_000L,
            keyframeIntervalMs = 500L,
            restartAfterMs = 3_000L,
        )

        watchdog.markConnected(0L)
        watchdog.observe(100L, bytesReceived = 10L, framesDecoded = 0L, connected = true)
        assertEquals(false, watchdog.latestObservationProgressed)

        watchdog.observe(200L, bytesReceived = 20L, framesDecoded = 1L, connected = true)
        assertEquals(true, watchdog.latestObservationProgressed)

        watchdog.observe(300L, bytesReceived = 30L, framesDecoded = 1L, connected = true)
        assertEquals(false, watchdog.latestObservationProgressed)
    }
}
