package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
