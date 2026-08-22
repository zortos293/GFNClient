package com.opencloudgaming.opennow

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun streamStatusBarDefaultsToConnectionEssentials() {
        val settings = AppSettings()
        val metrics = settings.streamStatsMetrics

        assertTrue(settings.showStatsOnLaunch)
        assertFalse(settings.hideStreamButtons)
        assertTrue(settings.externalMousePointerLock)
        assertFalse(settings.showFavoriteIconOnGameCards)
        assertTrue(settings.liveSelectedOutlines)
        assertFalse(settings.absoluteCinemaEffects)
        assertFalse(settings.localAppsEnabled)
        assertTrue(settings.localAppPackageNames.isEmpty())
        assertEquals(StreamKeyboardButtonPosition(), settings.streamKeyboardButtonPosition)
        assertTrue(metrics.fps)
        assertTrue(metrics.ping)
        assertFalse(metrics.bitrate)
        assertTrue(metrics.battery)
        assertTrue(metrics.connection)
        assertFalse(metrics.resolution)
        assertFalse(metrics.codec)
        assertFalse(metrics.location)
        assertFalse(metrics.latency)
        assertFalse(metrics.packetLoss)
        assertEquals(4, metrics.enabledCount())
    }

    @Test
    fun olderSavedSettingsReceiveStatusBarDefaults() {
        val settings = OpenNowJson.decodeFromString<AppSettings>("{}")

        assertEquals(StreamStatsMetrics(), settings.streamStatsMetrics)
        assertTrue(settings.showStatsOnLaunch)
        assertFalse(settings.hideStreamButtons)
        assertTrue(settings.externalMousePointerLock)
        assertEquals(StreamKeyboardButtonPosition(), settings.streamKeyboardButtonPosition)
        assertEquals(CatalogBackgroundPreset.ColorfulAbstract, settings.catalogBackgroundPreset)
        assertFalse(settings.analyticsConsentAsked)
        assertTrue(settings.analyticsOptOut)
        assertFalse(settings.analyticsSharingEnabled)
        assertFalse(settings.showFavoriteIconOnGameCards)
        assertTrue(settings.liveSelectedOutlines)
        assertFalse(settings.absoluteCinemaEffects)
        assertFalse(settings.localAppsEnabled)
        assertTrue(settings.localAppPackageNames.isEmpty())
        assertTrue(settings.showSessionReportAfterStream)
        assertEquals(TouchJoystickMode.Fixed, settings.androidTouch.joystickMode)
        assertEquals(TouchAimMode.LockJoystick, settings.androidTouch.aimMode)
        assertEquals(0f, settings.androidTouch.joystickDeadZone, 0.0001f)
    }

    @Test
    fun favoriteIconDefaultsOffAndPreservesExplicitOptIn() {
        val defaulted = OpenNowJson.decodeFromString<AppSettings>("{}")
        val optedIn = OpenNowJson.decodeFromString<AppSettings>(
            """{"showFavoriteIconOnGameCards":true}""",
        )

        assertFalse(defaulted.showFavoriteIconOnGameCards)
        assertTrue(optedIn.showFavoriteIconOnGameCards)
    }

    @Test
    fun liveSelectedOutlinesDefaultOnAndPreserveOptOut() {
        val defaulted = OpenNowJson.decodeFromString<AppSettings>("{}")
        val optedOut = OpenNowJson.decodeFromString<AppSettings>(
            """{"liveSelectedOutlines":false}""",
        )

        assertTrue(defaulted.liveSelectedOutlines)
        assertFalse(optedOut.liveSelectedOutlines)
    }

    @Test
    fun localAppsAreOptInAndSavedPackagesRemainCompatible() {
        val defaulted = OpenNowJson.decodeFromString<AppSettings>("{}")
        val optedIn = OpenNowJson.decodeFromString<AppSettings>(
            """{"localAppsEnabled":true,"localAppPackageNames":["com.epicgames.fortnite"]}""",
        )

        assertFalse(defaulted.localAppsEnabled)
        assertTrue(defaulted.localAppPackageNames.isEmpty())
        assertTrue(optedIn.localAppsEnabled)
        assertEquals(listOf("com.epicgames.fortnite"), optedIn.localAppPackageNames)
    }

    @Test
    fun legacyAbsoluteCinemaAccentMigratesToIndependentEffectToggle() {
        val cinema = OpenNowJson.decodeFromString<AppSettings>("""{"uiAccent":"AbsoluteCinema"}""").normalizedForAndroid()
        val switch = OpenNowJson.decodeFromString<AppSettings>("""{"uiAccent":"Switch"}""")

        assertEquals(UiAccent.OpenNow, cinema.uiAccent)
        assertTrue(cinema.absoluteCinemaEffects)
        assertEquals(UiAccent.Switch, switch.uiAccent)
    }

    @Test
    fun catalogueSortAndFiltersSurviveSettingsDecode() {
        val settings = OpenNowJson.decodeFromString<AppSettings>(
            """{"catalogSortId":"latest","catalogFilterIds":["genre-action","opennow:supported-controls:touchscreen"],"librarySortId":"recent","libraryFilterIds":["library_store:steam"]}""",
        ).normalizedForAndroid()

        assertEquals("latest", settings.catalogSortId)
        assertEquals(listOf("genre-action", CATALOG_FILTER_TOUCHSCREEN), settings.catalogFilterIds)
        assertEquals(LIBRARY_SORT_RECENT, settings.librarySortId)
        assertEquals(listOf("library_store:steam"), settings.libraryFilterIds)
    }

    @Test
    fun touchAimZoneIsOptInAndPersistsWhenSelected() {
        val defaulted = OpenNowJson.decodeFromString<AppSettings>("{}")
        val optedIn = OpenNowJson.decodeFromString<AppSettings>(
            """{"androidTouch":{"aimMode":"LockZone"}}""",
        )

        assertEquals(TouchAimMode.LockJoystick, defaulted.androidTouch.aimMode)
        assertEquals(TouchAimMode.LockZone, optedIn.androidTouch.aimMode)
    }

    @Test
    fun defaultsUseRecommendedProfileAndKeepOptionalMusicOff() {
        val settings = AppSettings()

        assertFalse(settings.nerdMode)
        assertEquals(CatalogBackgroundPreset.ColorfulAbstract, settings.catalogBackgroundPreset)
        assertTrue(settings.controllerUiSounds)
        assertEquals(AppLaunchPage.Store, settings.launchPage)
        assertEquals(StreamPreset.Recommended, settings.streamPreset)
        assertFalse(settings.streamIntroMusic)
        assertEquals(IntroMusicStartMode.Muted, settings.streamIntroStartMode)
        assertFalse(settings.queueReadyMusic)
        assertTrue(settings.showSessionReportAfterStream)
        assertFalse(settings.analyticsConsentAsked)
        assertTrue(settings.analyticsOptOut)
        assertFalse(settings.analyticsSharingEnabled)
        assertFalse(settings.stream.streamSharpeningEnabled)
    }

    @Test
    fun olderSavedSettingsKeepStreamSharpeningDisabledUnlessExplicitlyEnabled() {
        val defaulted = OpenNowJson.decodeFromString<AppSettings>("{}")
        val optedIn = OpenNowJson.decodeFromString<AppSettings>(
            """{"stream":{"streamSharpeningEnabled":true}}""",
        )

        assertFalse(defaulted.stream.streamSharpeningEnabled)
        assertTrue(optedIn.stream.streamSharpeningEnabled)
    }

    @Test
    fun mouseLockDefaultsOnAndPreservesExplicitOptOut() {
        val defaulted = OpenNowJson.decodeFromString<AppSettings>("{}")
        val optedOut = OpenNowJson.decodeFromString<AppSettings>(
            """{"externalMousePointerLock":false}""",
        )

        assertTrue(defaulted.externalMousePointerLock)
        assertFalse(optedOut.externalMousePointerLock)
    }

    @Test
    fun phonePresentationKeepsAspectFitByDefaultAndPreservesLaterOptIn() {
        val migrated = AppSettings().withCurrentStreamPresentationDefaults(androidTvProfile = false)

        assertFalse(migrated.legacyCropStreamToFill)
        assertFalse(migrated.stretchStreamToFit)
        assertEquals(STREAM_PRESENTATION_PROFILE_VERSION, migrated.streamPresentationProfileVersion)

        val optedIn = migrated.copy(stretchStreamToFit = true)
        assertEquals(optedIn, optedIn.withCurrentStreamPresentationDefaults(androidTvProfile = false))
    }

    @Test
    fun tvPresentationKeepsAspectFitByDefault() {
        val migrated = AppSettings().withCurrentStreamPresentationDefaults(androidTvProfile = true)

        assertFalse(migrated.legacyCropStreamToFill)
        assertFalse(migrated.stretchStreamToFit)
    }

    @Test
    fun legacyAnalyticsPreferenceDoesNotOptInWithoutConsent() {
        val settings = OpenNowJson.decodeFromString<AppSettings>("""{"analyticsOptOut":false}""")

        assertFalse(settings.analyticsConsentAsked)
        assertFalse(settings.analyticsSharingEnabled)
    }

    @Test
    fun sessionReportOptOutSurvivesSettingsSerialization() {
        val settings = OpenNowJson.decodeFromString<AppSettings>(
            """{"showSessionReportAfterStream":false}""",
        )

        assertFalse(settings.showSessionReportAfterStream)
    }

    @Test
    fun legacyPortalStreamModeMigratesToProviderTwentyOneByNineGeometry() {
        val normalized = AppSettings(
            stream = StreamSettings(
                resolution = "1376x640",
                aspectRatio = "19.5:9",
                fps = 120,
            ),
        ).normalizedForAndroid()

        assertEquals("1376x590", normalized.stream.resolution)
        assertEquals("21:9", normalized.stream.aspectRatio)
        assertEquals(120, normalized.stream.fps)
    }

    @Test
    fun persistedNonFiniteInputSettingsFallBackBeforeTheyReachMotionRounding() {
        val normalized = AppSettings(
            stream = StreamSettings(
                mouseSensitivity = Float.NaN,
                streamSharpeningAmount = Float.POSITIVE_INFINITY,
            ),
            posterSizeScale = Float.NaN,
            tvSafeAreaPaddingDp = Float.NEGATIVE_INFINITY,
            androidTouch = AndroidTouchSettings(
                opacity = Float.NaN,
                nativeTouchScrollScale = Float.POSITIVE_INFINITY,
                nativeTouchJitterThresholdDp = Float.NaN,
                offsets = mapOf("bad" to TouchOffset(Float.NaN, Float.POSITIVE_INFINITY)),
            ),
        ).normalizedForAndroid()

        assertEquals(1f, normalized.stream.mouseSensitivity, 0f)
        assertEquals(0.25f, normalized.stream.streamSharpeningAmount, 0f)
        assertEquals(1f, normalized.posterSizeScale, 0f)
        assertEquals(16f, normalized.tvSafeAreaPaddingDp, 0f)
        assertEquals(AndroidTouchSettings().opacity, normalized.androidTouch.opacity, 0f)
        assertEquals(1f, normalized.androidTouch.nativeTouchScrollScale, 0f)
        assertEquals(8f, normalized.androidTouch.nativeTouchJitterThresholdDp, 0f)
        assertEquals(TouchOffset(), normalized.androidTouch.offsets["bad"])
    }

    @Test
    fun fullyTransparentTouchControlsRemainInteractivePreference() {
        val normalized = AppSettings(
            androidTouch = AndroidTouchSettings(opacity = 0f),
        ).normalizedForAndroid()

        assertEquals(0f, normalized.androidTouch.opacity, 0f)
    }

    @Test
    fun keyboardButtonPositionIsKeptInsideTheStreamViewport() {
        val normalized = AppSettings(
            streamKeyboardButtonPosition = StreamKeyboardButtonPosition(
                horizontalFraction = Float.POSITIVE_INFINITY,
                verticalFraction = -0.25f,
            ),
        ).normalizedForAndroid()

        assertEquals(1f, normalized.streamKeyboardButtonPosition.horizontalFraction, 0f)
        assertEquals(0f, normalized.streamKeyboardButtonPosition.verticalFraction, 0f)
    }
}
