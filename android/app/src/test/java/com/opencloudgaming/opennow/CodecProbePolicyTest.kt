package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecProbePolicyTest {
    @Test
    fun android16ExynosHevcMainDecoderIsEligibleForWebRtcProbe() {
        assertTrue(
            isSupportedExynosHevcDecoder(
                codecName = "c2.exynos.hevc.decoder",
                sdkInt = 36,
                supportedTypes = listOf("video/hevc"),
                hevcProfiles = listOf(1),
            ),
        )
    }

    @Test
    fun android16ExynosHevcMain10DecoderIsEligibleForWebRtcProbe() {
        assertTrue(
            isSupportedExynosHevcDecoder(
                codecName = "OMX.Exynos.HEVC.Decoder",
                sdkInt = 36,
                supportedTypes = listOf("VIDEO/HEVC"),
                hevcProfiles = listOf(2),
            ),
        )
    }

    @Test
    fun android16ExynosHevcHdr10ProfileIsEligibleForWebRtcProbe() {
        assertTrue(
            isSupportedExynosHevcDecoder(
                codecName = "c2.exynos.hevc.decoder",
                sdkInt = 36,
                supportedTypes = listOf("video/hevc"),
                hevcProfiles = listOf(4_096),
            ),
        )
    }

    @Test
    fun legacyExynosHevcPolicyRemainsConservative() {
        assertFalse(
            isSupportedExynosHevcDecoder(
                codecName = "c2.exynos.hevc.decoder",
                sdkInt = 35,
                supportedTypes = listOf("video/hevc"),
                hevcProfiles = listOf(1, 2),
            ),
        )
    }

    @Test
    fun exynosDecoderWithoutSupportedHevcProfileIsNotAdvertised() {
        assertFalse(
            isSupportedExynosHevcDecoder(
                codecName = "c2.exynos.hevc.decoder",
                sdkInt = 36,
                supportedTypes = listOf("video/hevc"),
                hevcProfiles = listOf(4),
            ),
        )
    }

    @Test
    fun exynosAvcDecoderIsNotMistakenForHevc() {
        assertFalse(
            isSupportedExynosHevcDecoder(
                codecName = "c2.exynos.h264.decoder",
                sdkInt = 36,
                supportedTypes = listOf("video/avc"),
                hevcProfiles = listOf(1),
            ),
        )
    }
}
