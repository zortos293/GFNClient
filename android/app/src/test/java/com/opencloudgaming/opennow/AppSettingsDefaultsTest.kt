package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsDefaultsTest {
    @Test
    fun defaultsKeepAdvancedAndAudioPolishAvailableWithoutAutoplayingIntro() {
        val settings = AppSettings()

        assertTrue(settings.nerdMode)
        assertTrue(settings.controllerUiSounds)
        assertEquals(AppLaunchPage.Store, settings.launchPage)
        assertTrue(settings.streamIntroMusic)
        assertEquals(IntroMusicStartMode.Muted, settings.streamIntroStartMode)
        assertTrue(settings.queueReadyMusic)
    }
}
