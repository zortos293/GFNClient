package com.opencloudgaming.opennow

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingProfileInstrumentedTest {
    @Test
    fun commonResolutionAspectAndCodecMatrixResolvesToUsableHardwareProfile() {
        val report = CodecProbe.report(ApplicationProvider.getApplicationContext())
        val h264 = report.capabilities.firstOrNull { it.codec == VideoCodec.H264 }
        assertNotNull("Every supported Android target must provide H264 decoding", h264)
        assertTrue("H264 must be usable by the WebRTC launch path", h264?.streamingDecoderUsableForLaunch() == true)

        val modes = listOf(
            "1280x720" to "16:9",
            "1920x1080" to "16:9",
            "1920x1200" to "16:10",
            "1024x768" to "4:3",
            "1680x720" to "21:9",
            "2560x1080" to "21:9",
        )
        for ((resolution, aspectRatio) in modes) {
            for (codec in VideoCodec.entries) {
                val requested = StreamSettings(
                    resolution = resolution,
                    aspectRatio = aspectRatio,
                    fps = 60,
                    codec = codec,
                    colorQuality = if (codec == VideoCodec.H264) ColorQuality.EightBit420 else ColorQuality.TenBit420,
                )
                val adjusted = requested.adjustedForDevice(report)
                val effectiveCapability = report.capabilities.first { it.codec == adjusted.codec }

                assertTrue("$resolution $codec did not resolve to a usable decoder", effectiveCapability.streamingDecoderUsableForLaunch())
                assertEquals(
                    "$resolution $codec resolved with inconsistent aspect metadata",
                    streamAspectRatioForResolution(adjusted.resolution),
                    adjusted.aspectRatio,
                )
                println(
                    "STREAM_MATRIX requested=$resolution/$aspectRatio/$codec effective=${adjusted.resolution}/${adjusted.aspectRatio}/${adjusted.codec} " +
                        "fps=${adjusted.fps} decoder=${effectiveCapability.webRtcDecoderName ?: effectiveCapability.decoderName} " +
                        "max=${effectiveCapability.maxSupportedWidth}x${effectiveCapability.maxSupportedHeight}",
                )
            }
        }
    }
}
