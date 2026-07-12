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

        assertFalse(metrics.fps)
        assertTrue(metrics.ping)
        assertFalse(metrics.bitrate)
        assertTrue(metrics.battery)
        assertTrue(metrics.connection)
        assertFalse(metrics.resolution)
        assertFalse(metrics.codec)
        assertEquals(3, metrics.enabledCount())
    }

    @Test
    fun olderSavedSettingsReceiveStatusBarDefaults() {
        val settings = OpenNowJson.decodeFromString<AppSettings>("{}")

        assertEquals(StreamStatsMetrics(), settings.streamStatsMetrics)
        assertEquals(CatalogBackgroundPreset.ColorfulAbstract, settings.catalogBackgroundPreset)
        assertFalse(settings.analyticsConsentAsked)
        assertTrue(settings.analyticsOptOut)
        assertFalse(settings.analyticsSharingEnabled)
    }

    @Test
    fun defaultsKeepAdvancedAndAudioPolishAvailableWithoutAutoplayingIntro() {
        val settings = AppSettings()

        assertTrue(settings.nerdMode)
        assertEquals(CatalogBackgroundPreset.ColorfulAbstract, settings.catalogBackgroundPreset)
        assertTrue(settings.controllerUiSounds)
        assertEquals(AppLaunchPage.Store, settings.launchPage)
        assertTrue(settings.streamIntroMusic)
        assertEquals(IntroMusicStartMode.Muted, settings.streamIntroStartMode)
        assertTrue(settings.queueReadyMusic)
        assertFalse(settings.analyticsConsentAsked)
        assertTrue(settings.analyticsOptOut)
        assertFalse(settings.analyticsSharingEnabled)
    }

    @Test
    fun legacyAnalyticsPreferenceDoesNotOptInWithoutConsent() {
        val settings = OpenNowJson.decodeFromString<AppSettings>("""{"analyticsOptOut":false}""")

        assertFalse(settings.analyticsConsentAsked)
        assertFalse(settings.analyticsSharingEnabled)
    }
}
