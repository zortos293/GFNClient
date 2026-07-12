package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamResolutionTest {
    @Test
    fun smartSessionLimitsMatchMembershipTier() {
        val free = smartSessionLimitFor(SubscriptionInfo(membershipTier = "FREE"), null)
        val performance = smartSessionLimitFor(SubscriptionInfo(membershipTier = "PERFORMANCE"), null)
        val ultimate = smartSessionLimitFor(SubscriptionInfo(membershipTier = "ULTIMATE"), null)

        assertEquals(1, free.limitHours)
        assertEquals(SessionTimerMode.Countdown, free.mode)
        assertEquals(6, performance.limitHours)
        assertEquals(SessionTimerMode.Stopwatch, performance.mode)
        assertEquals(8, ultimate.limitHours)
        assertEquals(SessionTimerMode.Stopwatch, ultimate.mode)
    }

    @Test
    fun paidMonthlyUsageFallsBackToHundredHourLimit() {
        val subscription = SubscriptionInfo(membershipTier = "ULTIMATE", usedHours = 37.25)

        assertEquals(100.0, monthlyHourLimitFor(subscription, null) ?: 0.0, 0.001)
        assertEquals(62.75, monthlyHoursRemainingFor(subscription, null) ?: 0.0, 0.001)
    }

    @Test
    fun sessionWarningsFireAtMostRelevantCrossedThreshold() {
        assertEquals(null, sessionWarningThresholdCrossed(null, 30 * 60))
        assertEquals(30 * 60, sessionWarningThresholdCrossed(30 * 60 + 1, 30 * 60))
        assertEquals(5 * 60, sessionWarningThresholdCrossed(10 * 60 + 1, 5 * 60 - 1))
        assertEquals(null, sessionWarningThresholdCrossed(5 * 60, 5 * 60 - 1))
    }

    @Test
    fun streamResolutionPixelsKeepsSelected1080pFor16By9() {
        val settings = StreamSettings(resolution = "1920x1080", aspectRatio = "16:9")

        assertEquals(1920 to 1080, streamResolutionPixels(settings))
    }

    @Test
    fun runtimeResolutionMismatchIsDiagnosticForServerFallbackModes() {
        assertEquals(
            StreamResolutionMismatch(actualResolution = "1152x720", expectedResolution = "1280x720"),
            streamRuntimeResolutionMismatch(
                StreamSettings(resolution = "1280x720", aspectRatio = "16:9"),
                "1152x720",
            ),
        )
        assertEquals(
            StreamResolutionMismatch(actualResolution = "1366x768", expectedResolution = "1680x720"),
            streamRuntimeResolutionMismatch(
                StreamSettings(resolution = "1680x720", aspectRatio = "21:9"),
                "1366x768",
            ),
        )
    }

    @Test
    fun runtimeResolutionMismatchMarksServerNegotiatedFallback() {
        val mismatch = streamRuntimeResolutionMismatch(
            StreamSettings(resolution = "1680x720", aspectRatio = "21:9"),
            actualResolution = "1366x768",
            serverNegotiatedResolution = "1366x768",
        )

        assertEquals(
            StreamResolutionMismatch(
                actualResolution = "1366x768",
                expectedResolution = "1680x720",
                serverNegotiatedResolution = "1366x768",
            ),
            mismatch,
        )
        assertEquals(true, mismatch?.isServerNegotiatedFallback)
    }

    @Test
    fun runtimeResolutionMismatchIgnoresExactAndMissingStats() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9")

        assertEquals(null, streamRuntimeResolutionMismatch(settings, null))
        assertEquals(null, streamRuntimeResolutionMismatch(settings, "1680x720"))
    }

    @Test
    fun streamRendererAspectRatioIgnoresStartupPlaceholderResolution() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9")

        assertEquals(1680f / 720f, streamRendererAspectRatio(settings, "2x2"), 0.0001f)
    }

    @Test
    fun streamRendererAspectRatioKeepsRequestedAspectForUnexpectedFallbackStats() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9")

        assertEquals(1680f / 720f, streamRendererAspectRatio(settings, "1366x768"), 0.0001f)
    }

    @Test
    fun streamRendererAspectRatioUsesServerNegotiatedFallbackResolution() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9")

        assertEquals(
            1366f / 768f,
            streamRendererAspectRatio(
                settings = settings,
                decodedResolution = "1366x768",
                serverNegotiatedResolution = "1366x768",
            ),
            0.0001f,
        )
    }

    @Test
    fun streamResolutionPixelsMaps1080pTierToUltrawideMode() {
        val settings = StreamSettings(resolution = "1920x1080", aspectRatio = "21:9")

        assertEquals(2560 to 1080, streamResolutionPixels(settings))
    }

    @Test
    fun persistedUnsupportedAspectFallsBackToSupportedSixteenByNineMode() {
        val adjusted = StreamSettings(resolution = "1600x720", aspectRatio = "20:9")
            .withResolutionAllowed(SubscriptionInfo(membershipTier = "FREE"), null)

        assertEquals("16:9", adjusted.aspectRatio)
        assertEquals("1280x720", adjusted.resolution)
    }

    @Test
    fun freePlanResolutionNormalizationUses720pUltrawideFallback() {
        val freeSubscription = SubscriptionInfo(membershipTier = "FREE")

        assertEquals(
            "1680x720",
            normalizeStreamResolutionForAspectAndPlan("1920x1080", "21:9", freeSubscription, null),
        )
        assertEquals(
            "1680x720",
            StreamSettings(resolution = "2560x1080", aspectRatio = "21:9")
                .withResolutionAllowed(freeSubscription, null)
                .resolution,
        )
    }

    @Test
    fun customResolutionPixelsArePreservedForLaunch() {
        val settings = StreamSettings(resolution = "1728x720", aspectRatio = "21:9")

        assertEquals(1728 to 720, streamResolutionPixels(settings))
    }

    @Test
    fun freePlanKeepsCustomResolutionInsidePlanBounds() {
        val adjusted = StreamSettings(resolution = "1728x720", aspectRatio = "21:9")
            .withResolutionAllowed(SubscriptionInfo(membershipTier = "FREE"), null)

        assertEquals("1728x720", adjusted.resolution)
        assertEquals("21:9", adjusted.aspectRatio)
    }

    @Test
    fun freePlanClampsCustomResolutionOutsidePlanBounds() {
        val adjusted = StreamSettings(resolution = "5120x2160", aspectRatio = "21:9")
            .withResolutionAllowed(SubscriptionInfo(membershipTier = "FREE"), null)

        assertEquals("1680x720", adjusted.resolution)
        assertEquals("21:9", adjusted.aspectRatio)
    }

    @Test
    fun freePlanResolutionGuardFallsBackWhenAspectHasNoAvailableMode() {
        val adjusted = StreamSettings(resolution = "3840x1080", aspectRatio = "32:9")
            .withResolutionAllowed(SubscriptionInfo(membershipTier = "FREE"), null)

        assertEquals("1920x1080", adjusted.resolution)
        assertEquals("16:9", adjusted.aspectRatio)
    }

    @Test
    fun streamResolutionPixelsMaps1440pTierToUltrawideMode() {
        val settings = StreamSettings(resolution = "2560x1440", aspectRatio = "21:9")

        assertEquals(3440 to 1440, streamResolutionPixels(settings))
    }

    @Test
    fun streamResolutionPixelsKeepsSelected4kFor16By9() {
        val settings = StreamSettings(resolution = "3840x2160", aspectRatio = "16:9")

        assertEquals(3840 to 2160, streamResolutionPixels(settings))
    }

    @Test
    fun streamResolutionPixelsMapsSelectedTierForTallerAspectRatio() {
        val settings = StreamSettings(resolution = "1920x1080", aspectRatio = "16:10")

        assertEquals(1920 to 1200, streamResolutionPixels(settings))
    }

    @Test
    fun streamResolutionPixelsKeepsExactStoredUltrawideMode() {
        val settings = StreamSettings(resolution = "3440x1440", aspectRatio = "21:9")

        assertEquals(3440 to 1440, streamResolutionPixels(settings))
    }

    @Test
    fun streamResolutionOptionsIncludeAndroidSupportedModes() {
        assertEquals(
            listOf("1280x720", "1366x768", "1600x900", "1920x1080", "2560x1440", "3840x2160", "5120x2880"),
            streamResolutionOptionsForAspect("16:9"),
        )
        assertEquals(listOf("1024x768", "1112x834", "1600x1200"), streamResolutionOptionsForAspect("4:3"))
        assertEquals(listOf("1280x1024"), streamResolutionOptionsForAspect("5:4"))
        assertEquals(emptyList<String>(), streamResolutionOptionsForAspect("20:9"))
        assertEquals(listOf("1680x720", "2560x1080", "3440x1440", "5120x2160"), streamResolutionOptionsForAspect("21:9"))
        assertEquals(listOf("3840x1080", "5120x1440"), streamResolutionOptionsForAspect("32:9"))
    }

    @Test
    fun streamResolutionPixelsMaps4kTierToSupported16By10Mode() {
        val settings = StreamSettings(resolution = "3840x2160", aspectRatio = "16:10")

        assertEquals(3456 to 2160, streamResolutionPixels(settings))
    }

    @Test
    fun streamResolutionChoicesGatePriorityAndUltimateModes() {
        val freeSubscription = SubscriptionInfo(membershipTier = "FREE")
        val prioritySubscription = SubscriptionInfo(membershipTier = "PRIORITY")
        val ultimateSubscription = SubscriptionInfo(membershipTier = "ULTIMATE")
        val fhd = streamResolutionChoicesForAspect("16:9").first { it.value == "1920x1080" }
        val whd = streamResolutionChoicesForAspect("21:9").first { it.value == "1680x720" }
        val wfhd = streamResolutionChoicesForAspect("21:9").first { it.value == "2560x1080" }
        val qhd = streamResolutionChoicesForAspect("16:9").first { it.value == "2560x1440" }
        val fourK = streamResolutionChoicesForAspect("16:9").first { it.value == "3840x2160" }
        val fiveK = streamResolutionChoicesForAspect("16:9").first { it.value == "5120x2880" }

        assertEquals(true, fhd.isAvailableFor(freeSubscription, null))
        assertEquals(true, whd.isAvailableFor(freeSubscription, null))
        assertEquals(false, wfhd.isAvailableFor(freeSubscription, null))
        assertEquals(false, qhd.isAvailableFor(freeSubscription, null))
        assertEquals(true, fhd.isAvailableFor(prioritySubscription, null))
        assertEquals(true, whd.isAvailableFor(prioritySubscription, null))
        assertEquals(true, wfhd.isAvailableFor(prioritySubscription, null))
        assertEquals(true, qhd.isAvailableFor(prioritySubscription, null))
        assertEquals(false, fourK.isAvailableFor(prioritySubscription, null))
        assertEquals(false, fiveK.isAvailableFor(prioritySubscription, null))
        assertEquals(true, fourK.isAvailableFor(ultimateSubscription, null))
        assertEquals(true, fiveK.isAvailableFor(ultimateSubscription, null))
    }

    @Test
    fun streamFpsCapsFollowMembershipPlan() {
        val requested = StreamSettings(fps = 360)

        assertEquals(60, requested.withFpsAllowed(SubscriptionInfo(membershipTier = "FREE"), null).fps)
        assertEquals(60, requested.withFpsAllowed(SubscriptionInfo(membershipTier = "PERFORMANCE"), null).fps)
        assertEquals(360, requested.withFpsAllowed(SubscriptionInfo(membershipTier = "ULTIMATE"), null).fps)
        assertEquals(360, maxStreamFpsFor(null, "ULTIMATE"))
    }

    @Test
    fun authenticatedUltimateTierWinsWhenSubscriptionPayloadDefaultsToFree() {
        val incompleteSubscription = SubscriptionInfo(membershipTier = "FREE")
        val fourK = streamResolutionChoicesForAspect("16:9").first { it.value == "3840x2160" }
        val requested = StreamSettings(resolution = "3840x2160", aspectRatio = "16:9", fps = 360)

        assertEquals(true, fourK.isAvailableFor(incompleteSubscription, "ULTIMATE"))
        assertEquals(360, maxStreamFpsFor(incompleteSubscription, "ULTIMATE"))
        assertEquals(
            requested,
            requested
                .withResolutionAllowed(incompleteSubscription, "ULTIMATE")
                .withFpsAllowed(incompleteSubscription, "ULTIMATE"),
        )
    }

    @Test
    fun entitledResolutionDoesNotBypassMembershipPlanGate() {
        val subscription = SubscriptionInfo(
            membershipTier = "FREE",
            entitledResolutions = listOf(EntitledResolution(width = 3840, height = 2160, fps = 60)),
        )
        val fourK = streamResolutionChoicesForAspect("16:9").first { it.value == "3840x2160" }

        assertEquals(false, fourK.isAvailableFor(subscription, null))
    }

    @Test
    fun activeSessionRejectsUnexpectedResolutionBeforeReuse() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val stale = activeSession(
            resolution = "1680x1050",
            fps = 60,
            settingsSignature = streamSettingsSessionSignature(settings),
        )

        assertEquals(false, stale.matchesStreamSettings(settings))
    }

    @Test
    fun activeSessionRejectsServerFallbackResolutionBeforeReuse() {
        val ultrawide = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val hd = StreamSettings(resolution = "1280x720", aspectRatio = "16:9", fps = 60)

        assertEquals(
            false,
            activeSession(
                resolution = "1366x768",
                fps = 60,
                settingsSignature = streamSettingsSessionSignature(ultrawide),
            ).matchesStreamSettings(ultrawide),
        )
        assertEquals(
            false,
            activeSession(
                resolution = "1280x720",
                fps = 60,
                settingsSignature = streamSettingsSessionSignature(ultrawide),
            ).matchesStreamSettings(ultrawide),
        )
        assertEquals(
            false,
            activeSession(
                resolution = "1152x720",
                fps = 60,
                settingsSignature = streamSettingsSessionSignature(hd),
            ).matchesStreamSettings(hd),
        )
    }

    @Test
    fun activeSessionMatchesRequestedUltrawideResolutionBeforeReuse() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val active = activeSession(
            resolution = "1680x720",
            fps = 60,
            settingsSignature = streamSettingsSessionSignature(settings),
        )

        assertEquals(true, active.matchesStreamSettings(settings))
    }

    @Test
    fun activeSessionWithoutOpenNowSettingsSignatureIsNotReusedForLaunch() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val active = activeSession(resolution = "1680x720", fps = 60)

        assertEquals(false, active.matchesStreamSettings(settings))
    }

    @Test
    fun activeSessionWithDifferentOpenNowSettingsSignatureIsNotReusedForLaunch() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60, codec = VideoCodec.H265, maxBitrateMbps = 150)
        val otherSettings = settings.copy(codec = VideoCodec.H264, maxBitrateMbps = 75)
        val active = activeSession(
            resolution = "1680x720",
            fps = 60,
            settingsSignature = streamSettingsSessionSignature(otherSettings),
        )

        assertEquals(false, active.matchesStreamSettings(settings))
    }

    @Test
    fun activeSessionWithUnknownMonitorModeIsNotReusedForLaunch() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val active = activeSession(
            resolution = null,
            fps = null,
            settingsSignature = streamSettingsSessionSignature(settings),
        )

        assertEquals(false, active.matchesStreamSettings(settings))
    }

    @Test
    fun activeSessionWithUnknownRefreshRateIsNotReusedForLaunch() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val active = activeSession(
            resolution = "1680x720",
            fps = null,
            settingsSignature = streamSettingsSessionSignature(settings),
        )

        assertEquals(false, active.matchesStreamSettings(settings))
    }

    @Test
    fun activeSessionConflictPrefersSameRequestedAppBeforePolling() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val otherReady = activeSession(
            sessionId = "other-ready",
            appId = 200,
            resolution = "1680x720",
            fps = 60,
            settingsSignature = streamSettingsSessionSignature(settings),
        )
        val sameAppLaunching = activeSession(
            sessionId = "same-launching",
            appId = 100,
            status = 1,
            resolution = "1680x720",
            fps = 60,
            settingsSignature = streamSettingsSessionSignature(settings),
        )

        assertEquals(
            "same-launching",
            activeSessionLaunchConflict(listOf(otherReady, sameAppLaunching), launchAppId = 100, settings = settings)?.sessionId,
        )
    }

    @Test
    fun activeSessionConflictStillReturnsMismatchedExistingSessionForUserChoice() {
        val settings = StreamSettings(resolution = "1680x720", aspectRatio = "21:9", fps = 60)
        val mismatched = activeSession(
            sessionId = "mismatched",
            appId = 100,
            resolution = "1920x1080",
            fps = 60,
            settingsSignature = streamSettingsSessionSignature(StreamSettings(resolution = "1920x1080", aspectRatio = "16:9", fps = 60)),
        )

        assertEquals(
            "mismatched",
            activeSessionLaunchConflict(listOf(mismatched), launchAppId = 100, settings = settings)?.sessionId,
        )
    }

    private fun activeSession(
        sessionId: String = "session",
        appId: Int = 100,
        status: Int = 2,
        resolution: String?,
        fps: Int?,
        settingsSignature: String? = null,
    ): ActiveSessionInfo =
        ActiveSessionInfo(
            sessionId = sessionId,
            appId = appId,
            status = status,
            serverIp = "127.0.0.1",
            signalingUrl = "wss://127.0.0.1/nvst/",
            resolution = resolution,
            fps = fps,
            settingsSignature = settingsSignature,
        )
}
