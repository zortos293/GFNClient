package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSettingsDeviceAdjustmentTest {
    @Test
    fun preservesSelectedH265WhenWebRtcHardwareDecoderExists() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit420)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.H265,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
    }

    @Test
    fun preservesSelectedAv1WhenWebRtcHardwareDecoderExists() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, colorQuality = ColorQuality.TenBit420)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
    }

    @Test
    fun av1DropsChroma444BeforeLaunch() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, colorQuality = ColorQuality.EightBit444)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
    }

    @Test
    fun av1HdrPreservesTenBit420Profile() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, colorQuality = ColorQuality.TenBit444, hdrEnabled = true)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(ColorQuality.TenBit420, adjusted.colorQuality)
        assertEquals(true, adjusted.hdrEnabled)
    }

    @Test
    fun androidSettingsAvailabilityAllowsCodecsAndWithholdsChroma444() {
        assertTrue(VideoCodec.AV1.availableForAndroidSettings())
        assertTrue(VideoCodec.H264.availableForAndroidSettings())
        assertTrue(VideoCodec.H265.availableForAndroidSettings())
        assertFalse(ColorQuality.EightBit444.availableForCodec(VideoCodec.H265))
        assertFalse(ColorQuality.TenBit444.availableForCodec(VideoCodec.H265))
        assertTrue(ColorQuality.EightBit420.availableForCodec(VideoCodec.H265))
        assertTrue(ColorQuality.TenBit420.availableForCodec(VideoCodec.H265))
    }

    @Test
    fun chroma444SettingsNormalizeTo420ForAndroid() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit444)
            .withCodecColorCompatibility()

        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.TenBit420, adjusted.colorQuality)
    }

    @Test
    fun streamPresetsApplyExpectedAndroidProfiles() {
        val base = StreamSettings(aspectRatio = "21:9", resolution = "1680x720", codec = VideoCodec.AV1, colorQuality = ColorQuality.EightBit444)

        val low = base.applyingStreamPreset(StreamPreset.LowDataSaver)
        assertEquals("1680x720", low.resolution)
        assertEquals("21:9", low.aspectRatio)
        assertEquals(30, low.fps)
        assertEquals(12, low.maxBitrateMbps)
        assertEquals(VideoCodec.AV1, low.codec)
        assertEquals(ColorQuality.EightBit420, low.colorQuality)

        val medium = base.applyingStreamPreset(StreamPreset.Medium)
        assertEquals("2560x1080", medium.resolution)
        assertEquals(60, medium.fps)
        assertEquals(35, medium.maxBitrateMbps)

        val high = base.applyingStreamPreset(StreamPreset.High)
        assertEquals("3440x1440", high.resolution)
        assertEquals(360, high.fps)
        assertEquals(75, high.maxBitrateMbps)
    }

    @Test
    fun preservesUltimate360FpsForStableAndroidProfile() {
        val adjusted = StreamSettings(resolution = "1920x1080", aspectRatio = "16:9", fps = 360, codec = VideoCodec.AV1)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(360, adjusted.fps)
    }

    @Test
    fun preservesTenBitWhenHdrIsEnabled() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit420, hdrEnabled = true)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.H265,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.TenBit420, adjusted.colorQuality)
    }

    @Test
    fun fallsBackToH264WhenSelectedDecoderHasNoHardwarePath() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, colorQuality = ColorQuality.TenBit420, maxBitrateMbps = 90)
            .adjustedForDevice(codecReport(VideoCodec.AV1, hardwareDecoder = false, realtimeSafe = false))

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals(35, adjusted.maxBitrateMbps)
    }

    @Test
    fun fallsBackToH264WhenH265WebRtcDecoderIsSoftwareOnly() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit420, maxBitrateMbps = 90)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.H265,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = false,
                ),
            )

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals(35, adjusted.maxBitrateMbps)
    }

    @Test
    fun preservesSelectedResolutionWhenFallingBackToH264() {
        val adjusted = StreamSettings(
            resolution = "1680x720",
            aspectRatio = "21:9",
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            maxBitrateMbps = 90,
        )
            .adjustedForDevice(
                codecReport(
                    VideoCodec.H265,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = false,
                ),
            )

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals("1680x720", adjusted.resolution)
        assertEquals("21:9", adjusted.aspectRatio)
        assertEquals(35, adjusted.maxBitrateMbps)
    }

    @Test
    fun fallsBackToH264WhenH265OnlyHasPlatformHardwareDecoder() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit420, maxBitrateMbps = 90)
            .adjustedForDevice(codecReport(VideoCodec.H265, hardwareDecoder = true, realtimeSafe = true))

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals(35, adjusted.maxBitrateMbps)
    }

    @Test
    fun preservesH265WhenWebRtcHardwareDecoderWorksButNativeProbeFails() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit420, maxBitrateMbps = 90)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.H265,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    nativeDecoderAvailable = false,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals(75, adjusted.maxBitrateMbps)
    }

    @Test
    fun changingResolutionDoesNotForceAWebRtcHardwareCodecBackToH264() {
        val report = codecReport(
            VideoCodec.H265,
            hardwareDecoder = true,
            realtimeSafe = true,
            nativeDecoderAvailable = false,
            webRtcDecoderAvailable = true,
            webRtcHardwareDecoderAvailable = true,
        )

        listOf("1280x720", "1920x1080", "2560x1440", "3840x2160").forEach { resolution ->
            val adjusted = StreamSettings(
                resolution = resolution,
                aspectRatio = "16:9",
                codec = VideoCodec.H265,
            ).adjustedForDevice(report)

            assertEquals("$resolution should retain the selected codec", VideoCodec.H265, adjusted.codec)
        }
    }

    @Test
    fun selectsAv1WhenNativeAndWebRtcHardwarePathsExist() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, colorQuality = ColorQuality.TenBit420)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    nativeDecoderAvailable = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
    }

    @Test
    fun keepsSelectedCodecOnLowPowerDevicesWhenWebRtcHardwareDecoderExists() {
        val adjusted = StreamSettings(codec = VideoCodec.H265, colorQuality = ColorQuality.TenBit420, maxBitrateMbps = 90)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.H265,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    lowPower = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals(25, adjusted.maxBitrateMbps)
    }

    @Test
    fun preservesHighRefreshRateForSupportedAndroidStreams() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, fps = 120, maxBitrateMbps = 90)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    hardwareDecoder = true,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(120, adjusted.fps)
        assertEquals(75, adjusted.maxBitrateMbps)
    }

    @Test
    fun capsH264AndroidBandwidthAtUserSafeCeiling() {
        val adjusted = StreamSettings(codec = VideoCodec.H264, maxBitrateMbps = 150)
            .adjustedForDevice(codecReport(VideoCodec.H264, hardwareDecoder = true, realtimeSafe = true))

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(75, adjusted.maxBitrateMbps)
    }

    @Test
    fun stabilizesExtremeH264CloudMatchProfileBeforeLaunch() {
        val adjusted = StreamSettings(
            resolution = "5120x1440",
            aspectRatio = "32:9",
            fps = 240,
            maxBitrateMbps = 150,
            codec = VideoCodec.H264,
            colorQuality = ColorQuality.TenBit444,
            hdrEnabled = true,
            enableL4S = true,
            enableCloudGsync = true,
            streamSharpeningEnabled = true,
        ).adjustedForDevice(codecReport(VideoCodec.H264, hardwareDecoder = true, realtimeSafe = true))

        assertEquals("5120x1440", adjusted.resolution)
        assertEquals("32:9", adjusted.aspectRatio)
        assertEquals(120, adjusted.fps)
        assertEquals(75, adjusted.maxBitrateMbps)
        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals(false, adjusted.hdrEnabled)
        assertEquals(false, adjusted.enableCloudGsync)
        assertEquals(true, adjusted.enableL4S)
    }

    @Test
    fun stabilizesExtremeH265CloudMatchProfileWithoutDroppingAdvancedFeatures() {
        val adjusted = StreamSettings(
            resolution = "5120x2160",
            aspectRatio = "21:9",
            fps = 240,
            maxBitrateMbps = 150,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
            enableL4S = true,
            enableCloudGsync = true,
            streamSharpeningEnabled = true,
        ).adjustedForDevice(
            codecReport(
                VideoCodec.H265,
                hardwareDecoder = true,
                realtimeSafe = true,
                nativeDecoderAvailable = true,
                webRtcDecoderAvailable = true,
                webRtcHardwareDecoderAvailable = true,
            ),
        )

        assertEquals("5120x2160", adjusted.resolution)
        assertEquals("21:9", adjusted.aspectRatio)
        assertEquals(120, adjusted.fps)
        assertEquals(75, adjusted.maxBitrateMbps)
        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.TenBit420, adjusted.colorQuality)
        assertEquals(true, adjusted.hdrEnabled)
        assertEquals(true, adjusted.enableCloudGsync)
        assertEquals(true, adjusted.enableL4S)
    }

    @Test
    fun safeVideoFallbackUsesBasicWorkingAndroidProfile() {
        val fallback = StreamSettings(
            resolution = "3840x2160",
            aspectRatio = "16:9",
            fps = 120,
            maxBitrateMbps = 150,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
            enableCloudGsync = true,
            streamSharpeningEnabled = true,
        ).androidSafeVideoFallback()

        assertEquals("1920x1080", fallback.resolution)
        assertEquals("16:9", fallback.aspectRatio)
        assertEquals(60, fallback.fps)
        assertEquals(75, fallback.maxBitrateMbps)
        assertEquals(VideoCodec.H264, fallback.codec)
        assertEquals(ColorQuality.EightBit420, fallback.colorQuality)
        assertEquals(false, fallback.hdrEnabled)
        assertEquals(false, fallback.enableCloudGsync)
        assertEquals(false, fallback.streamSharpeningEnabled)
    }

    @Test
    fun safeVideoFallbackPreservesLaunchResolutionInsideH264Bounds() {
        val fallback = StreamSettings(
            resolution = "1680x720",
            aspectRatio = "21:9",
            fps = 60,
            maxBitrateMbps = 75,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
        ).androidSafeVideoFallback()

        assertEquals("1680x720", fallback.resolution)
        assertEquals("21:9", fallback.aspectRatio)
        assertEquals(60, fallback.fps)
        assertEquals(75, fallback.maxBitrateMbps)
        assertEquals(VideoCodec.H264, fallback.codec)
        assertEquals(ColorQuality.EightBit420, fallback.colorQuality)
    }

    @Test
    fun usesSafeH264ProfileForLowPowerAndroidTv() {
        val adjusted = StreamSettings(
            resolution = "3840x2160",
            aspectRatio = "16:9",
            fps = 120,
            maxBitrateMbps = 90,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            streamSharpeningEnabled = true,
        ).adjustedForDevice(codecReport(VideoCodec.H265, hardwareDecoder = true, realtimeSafe = true, lowPower = true, tv = true))

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals("1920x1080", adjusted.resolution)
        assertEquals("16:9", adjusted.aspectRatio)
        assertEquals(60, adjusted.fps)
        assertEquals(25, adjusted.maxBitrateMbps)
        assertEquals(false, adjusted.streamSharpeningEnabled)
    }

    @Test
    fun preservesHardwareH265ForLowPowerAndroidTvInsideSafeLimits() {
        val adjusted = StreamSettings(
            resolution = "3840x2160",
            aspectRatio = "16:9",
            fps = 120,
            maxBitrateMbps = 90,
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
            hdrEnabled = true,
            enableCloudGsync = true,
            streamSharpeningEnabled = true,
        ).adjustedForDevice(
            codecReport(
                VideoCodec.H265,
                hardwareDecoder = true,
                realtimeSafe = true,
                lowPower = true,
                tv = true,
                nativeDecoderAvailable = true,
                webRtcDecoderAvailable = true,
                webRtcHardwareDecoderAvailable = true,
            ),
        )

        assertEquals(VideoCodec.H265, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals("1920x1080", adjusted.resolution)
        assertEquals(60, adjusted.fps)
        assertEquals(25, adjusted.maxBitrateMbps)
        assertEquals(false, adjusted.hdrEnabled)
        assertEquals(false, adjusted.enableCloudGsync)
        assertEquals(false, adjusted.streamSharpeningEnabled)
    }

    @Test
    fun keepsLowPowerAndroidTvUltrawideWithinDecoderBounds() {
        val adjusted = StreamSettings(
            resolution = "3440x1440",
            aspectRatio = "21:9",
            codec = VideoCodec.H265,
            colorQuality = ColorQuality.TenBit420,
        ).adjustedForDevice(codecReport(VideoCodec.H265, hardwareDecoder = true, realtimeSafe = true, lowPower = true, tv = true))

        assertEquals(VideoCodec.H264, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
        assertEquals("2560x1080", adjusted.resolution)
        assertEquals("21:9", adjusted.aspectRatio)
    }

    @Test
    fun disablesRendererSharpeningForAndroidTvLaunchProfiles() {
        val adjusted = StreamSettings(
            codec = VideoCodec.AV1,
            maxBitrateMbps = 75,
            streamSharpeningEnabled = true,
        ).adjustedForDevice(
            codecReport(
                VideoCodec.AV1,
                hardwareDecoder = true,
                realtimeSafe = true,
                tv = true,
                webRtcDecoderAvailable = true,
                webRtcHardwareDecoderAvailable = true,
            ),
        )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(35, adjusted.maxBitrateMbps)
        assertEquals(false, adjusted.streamSharpeningEnabled)
    }

    @Test
    fun preservesAv1WhenWebRtcHardwarePathExistsAndPlatformProbeMissesIt() {
        val adjusted = StreamSettings(codec = VideoCodec.AV1, colorQuality = ColorQuality.TenBit420)
            .adjustedForDevice(
                codecReport(
                    VideoCodec.AV1,
                    decoderAvailable = false,
                    hardwareDecoder = false,
                    realtimeSafe = false,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
        )

        assertEquals(VideoCodec.AV1, adjusted.codec)
        assertEquals(ColorQuality.EightBit420, adjusted.colorQuality)
    }

    @Test
    fun usesAv1HardwareFallbackWhenH264ProbeFails() {
        val report = RuntimeCodecReport(
            capabilities = listOf(
                CodecCapability(
                    codec = VideoCodec.H264,
                    decoderAvailable = true,
                    encoderAvailable = false,
                    hardwareDecoder = true,
                    hardwareEncoder = false,
                    realtimeSafe = true,
                    webRtcDecoderAvailable = false,
                ),
                CodecCapability(
                    codec = VideoCodec.AV1,
                    decoderAvailable = false,
                    encoderAvailable = false,
                    hardwareDecoder = false,
                    hardwareEncoder = false,
                    realtimeSafe = false,
                    webRtcDecoderAvailable = true,
                    webRtcHardwareDecoderAvailable = true,
                ),
            ),
            nativeRuntimeSummary = "{}",
            androidTvProfile = false,
            lowPowerGpuProfile = false,
        )

        val adjusted = StreamSettings(codec = VideoCodec.H264, colorQuality = ColorQuality.EightBit420)
            .adjustedForDevice(report)

        assertEquals(VideoCodec.AV1, adjusted.codec)
    }

    @Test
    fun capsResolutionToDecoderCapabilities() {
        val report = RuntimeCodecReport(
            capabilities = listOf(
                CodecCapability(
                    codec = VideoCodec.H264,
                    decoderAvailable = true,
                    encoderAvailable = false,
                    hardwareDecoder = true,
                    hardwareEncoder = false,
                    realtimeSafe = true,
                    maxSupportedWidth = 1920,
                    maxSupportedHeight = 1080,
                )
            ),
            nativeRuntimeSummary = "{}",
            androidTvProfile = false,
            lowPowerGpuProfile = false,
        )

        val settings = StreamSettings(
            resolution = "3440x1440",
            aspectRatio = "21:9",
            codec = VideoCodec.H264,
        )

        val adjusted = settings.adjustedForDevice(report)
        assertEquals("2560x1080", adjusted.resolution)
    }

    @Test
    fun testAdjustedForDeviceCapsFpsBasedOnResolutionCapabilities() {
        val report = codecReport(
            codec = VideoCodec.H265,
            hardwareDecoder = true,
            realtimeSafe = true,
            webRtcDecoderAvailable = true,
            webRtcHardwareDecoderAvailable = true,
            maxSupportedWidth = 3840,
            maxSupportedHeight = 2160,
            maxFpsByResolution = mapOf(
                "3456x2160" to 60,
                "2560x1600" to 120,
            )
        )

        val settings = StreamSettings(
            resolution = "3456x2160",
            aspectRatio = "16:10",
            fps = 120,
            codec = VideoCodec.H265,
        )

        val adjusted = settings.adjustedForDevice(report)
        assertEquals("3456x2160", adjusted.resolution)
        assertEquals(60, adjusted.fps)
    }

    private fun codecReport(
        codec: VideoCodec,
        decoderAvailable: Boolean = true,
        hardwareDecoder: Boolean,
        realtimeSafe: Boolean,
        lowPower: Boolean = false,
        tv: Boolean = false,
        nativeDecoderAvailable: Boolean? = null,
        webRtcDecoderAvailable: Boolean? = null,
        webRtcHardwareDecoderAvailable: Boolean? = null,
        maxSupportedWidth: Int? = null,
        maxSupportedHeight: Int? = null,
        maxFpsByResolution: Map<String, Int> = emptyMap(),
    ): RuntimeCodecReport =
        RuntimeCodecReport(
            capabilities = listOf(
                CodecCapability(
                    codec = codec,
                    decoderAvailable = decoderAvailable,
                    encoderAvailable = false,
                    hardwareDecoder = hardwareDecoder,
                    hardwareEncoder = false,
                    realtimeSafe = realtimeSafe,
                    nativeDecoderAvailable = nativeDecoderAvailable,
                    webRtcDecoderAvailable = webRtcDecoderAvailable,
                    webRtcHardwareDecoderAvailable = webRtcHardwareDecoderAvailable,
                    maxSupportedWidth = maxSupportedWidth,
                    maxSupportedHeight = maxSupportedHeight,
                    maxFpsByResolution = maxFpsByResolution,
                ),
            ),
            nativeRuntimeSummary = "{}",
            androidTvProfile = tv,
            lowPowerGpuProfile = lowPower,
        )
}
