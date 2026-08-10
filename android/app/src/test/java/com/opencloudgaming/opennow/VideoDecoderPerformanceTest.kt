package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.EncodedImage
import org.webrtc.VideoCodecStatus
import org.webrtc.VideoDecoder

class VideoDecoderPerformanceTest {
    @Test
    fun highFpsDecoderTuningPreservesExactUserSelection() {
        assertEquals(120, mediaCodecPerformanceTargetFps(120))
        assertEquals(240, mediaCodecPerformanceTargetFps(240))
        assertEquals(340, mediaCodecPerformanceTargetFps(340))
        assertEquals(360, mediaCodecPerformanceTargetFps(360))
    }

    @Test
    fun sixtyFpsUsesRealtimeDecoderScheduling() {
        assertEquals(60, mediaCodecPerformanceTargetFps(60))
    }

    @Test
    fun lowFpsDoesNotRequireDecoderPerformanceOverride() {
        assertNull(mediaCodecPerformanceTargetFps(30))
    }

    @Test
    fun approvedHardwareDecoderGetsMediaCodecTuning() {
        val hardwareDecoder = fakeDecoder()

        assertTrue(
            shouldUseMediaCodecDecoderTuning(
                selectedDecoder = hardwareDecoder,
                approvedHardwareDecoder = hardwareDecoder,
                requestedFps = 60,
                lowLatencyEnabled = false,
            ),
        )
    }

    @Test
    fun nativeFallbackIsNotWrappedForPerformanceTuning() {
        assertFalse(
            shouldUseMediaCodecDecoderTuning(
                selectedDecoder = fakeDecoder(),
                approvedHardwareDecoder = null,
                requestedFps = 60,
                lowLatencyEnabled = false,
            ),
        )
    }

    @Test
    fun pixel6aExynosFallbackIsNotWrappedAtSixtyFps() {
        assertFalse(
            shouldUseMediaCodecDecoderTuning(
                selectedDecoder = fakeDecoder("c2.exynos.h264.decoder"),
                approvedHardwareDecoder = null,
                requestedFps = 60,
                lowLatencyEnabled = false,
            ),
        )
    }

    @Test
    fun nativeFallbackIsNotWrappedForLowLatencyTuning() {
        assertFalse(
            shouldUseMediaCodecDecoderTuning(
                selectedDecoder = fakeDecoder(),
                approvedHardwareDecoder = null,
                requestedFps = 30,
                lowLatencyEnabled = true,
            ),
        )
    }

    private fun fakeDecoder(implementationName: String = "test"): VideoDecoder =
        object : VideoDecoder {
            override fun initDecode(
                settings: VideoDecoder.Settings?,
                decodeCallback: VideoDecoder.Callback?,
            ): VideoCodecStatus = error("unused")

            override fun release(): VideoCodecStatus = error("unused")

            override fun decode(
                frame: EncodedImage?,
                info: VideoDecoder.DecodeInfo?,
            ): VideoCodecStatus = error("unused")

            override fun getImplementationName(): String = implementationName
        }
}
