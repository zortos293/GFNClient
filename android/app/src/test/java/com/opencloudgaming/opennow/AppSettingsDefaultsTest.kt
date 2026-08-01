package com.opencloudgaming.opennow

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun streamStatusBarDefaultsToConnectionEssentials() {
        val metrics = AppSettings().streamStatsMetrics

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
        assertEquals(CatalogBackgroundPreset.ColorfulAbstract, settings.catalogBackgroundPreset)
        assertFalse(settings.analyticsConsentAsked)
        assertTrue(settings.analyticsOptOut)
        assertFalse(settings.analyticsSharingEnabled)
        assertTrue(settings.showSessionReportAfterStream)
        assertEquals(TouchJoystickMode.Fixed, settings.androidTouch.joystickMode)
        assertEquals(0f, settings.androidTouch.joystickDeadZone, 0.0001f)
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
}
