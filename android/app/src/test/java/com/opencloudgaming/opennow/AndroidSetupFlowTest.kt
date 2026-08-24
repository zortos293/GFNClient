package com.opencloudgaming.opennow

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSetupFlowTest {
    @Test
    fun `the subtle adaptive background is default and nothing stays distinct`() {
        val defaults = AppSettings()
        val nothing = defaults.withAppBackgroundChoice(AppBackgroundChoice.Nothing)
        val wallpaper = defaults.withAppBackgroundChoice(AppBackgroundChoice.Wallpaper)

        assertEquals(AppBackgroundChoice.Default, appBackgroundChoiceFor(defaults))
        assertTrue(defaults.ambientBackgroundEnabled)
        assertEquals(AppBackgroundChoice.Nothing, appBackgroundChoiceFor(nothing))
        assertFalse(nothing.ambientBackgroundEnabled)
        assertFalse(nothing.nerdCatalogBackground)
        assertEquals(AppBackgroundChoice.Wallpaper, appBackgroundChoiceFor(wallpaper))
        assertTrue(wallpaper.nerdCatalogBackground)
    }

    @Test
    fun `a fresh install runs setup and a finished one does not`() {
        assertTrue(shouldShowSetupFlow(AppSettings()))
        assertTrue(shouldShowSetupFlow(OpenNowJson.decodeFromString<AppSettings>("{}")))
        assertFalse(shouldShowSetupFlow(AppSettings().completingSetupFlow(SetupStep.Ready)))
    }

    @Test
    fun `raising the flow version brings existing installs back through setup`() {
        val completedOnAnOlderRelease = AppSettings(setupFlowCompletedVersion = SETUP_FLOW_VERSION - 1)

        assertTrue(shouldShowSetupFlow(completedOnAnOlderRelease))
    }

    @Test
    fun `running setup again from settings does not disturb the choices it made`() {
        val configured = AppSettings(
            uiAccent = UiAccent.Violet,
            nerdCatalogBackground = true,
            catalogBackgroundPreset = CatalogBackgroundPreset.AbsoluteCinema,
            analyticsConsentAsked = true,
            analyticsOptOut = false,
        ).completingSetupFlow(SetupStep.Ready)

        val restarted = configured.restartingSetupFlow()

        assertTrue(shouldShowSetupFlow(restarted))
        assertEquals(configured.copy(setupFlowCompletedVersion = 0), restarted)
    }

    @Test
    fun `steps run welcome to ready with no gaps at either end`() {
        assertEquals(
            listOf(
                SetupStep.Welcome,
                SetupStep.Appearance,
                SetupStep.Streaming,
                SetupStep.Feedback,
                SetupStep.Ready,
            ),
            setupSteps(),
        )
        assertNull(setupStepBefore(SetupStep.Welcome))
        assertNull(setupStepAfter(SetupStep.Ready))
        assertTrue(isFinalSetupStep(SetupStep.Ready))
        assertFalse(isFinalSetupStep(SetupStep.Feedback))

        var step = SetupStep.Welcome
        val walked = mutableListOf(step)
        while (!isFinalSetupStep(step)) {
            step = setupStepAfter(step)!!
            walked += step
        }
        assertEquals(setupSteps(), walked)
        assertEquals(SetupStep.Feedback, setupStepBefore(SetupStep.Ready))
    }

    @Test
    fun `leaving before the diagnostics step still lets the consent dialog ask`() {
        listOf(SetupStep.Welcome, SetupStep.Appearance, SetupStep.Streaming, SetupStep.Feedback)
            .forEach { furthest ->
                val settings = AppSettings().completingSetupFlow(furthest)

                assertFalse(
                    "furthest=$furthest should leave analytics consent unasked",
                    settings.analyticsConsentAsked,
                )
                assertFalse(shouldShowSetupFlow(settings))
            }
    }

    @Test
    fun `walking past the diagnostics step counts as answering it`() {
        val settings = AppSettings().completingSetupFlow(SetupStep.Ready)

        assertTrue(settings.analyticsConsentAsked)
        // Reaching the step is consent to have been asked, not consent to share.
        assertTrue(settings.analyticsOptOut)
        assertFalse(settings.analyticsSharingEnabled)
    }

    @Test
    fun `an answer given during setup survives skipping out afterwards`() {
        val optedIn = AppSettings(analyticsConsentAsked = true, analyticsOptOut = false)

        val settings = optedIn.completingSetupFlow(SetupStep.Feedback)

        assertTrue(settings.analyticsConsentAsked)
        assertTrue(settings.analyticsSharingEnabled)
    }

    @Test
    fun `the streaming step reflects the preset already in settings`() {
        assertEquals(
            SetupStreamingChoice.Recommended,
            setupStreamingChoiceFor(AppSettings(streamPreset = StreamPreset.Recommended)),
        )
        assertEquals(
            SetupStreamingChoice.Best,
            setupStreamingChoiceFor(AppSettings(streamPreset = StreamPreset.High)),
        )
        assertEquals(
            SetupStreamingChoice.DataSaver,
            setupStreamingChoiceFor(AppSettings(streamPreset = StreamPreset.LowDataSaver)),
        )
        listOf(StreamPreset.Custom, StreamPreset.Medium).forEach { preset ->
            assertEquals(
                SetupStreamingChoice.Custom,
                setupStreamingChoiceFor(AppSettings(streamPreset = preset)),
            )
        }
    }

    @Test
    fun `every streaming choice round-trips through its preset`() {
        SetupStreamingChoice.entries.forEach { choice ->
            val preset = setupStreamingPresetFor(choice)
            assertEquals(
                choice.name,
                choice,
                setupStreamingChoiceFor(AppSettings(streamPreset = preset)),
            )
        }
    }

    @Test
    fun `only the custom choice exposes the inline stream controls`() {
        SetupStreamingChoice.entries.forEach { choice ->
            assertEquals(
                choice.name,
                choice == SetupStreamingChoice.Custom,
                setupStreamingCustomControlsVisible(choice),
            )
        }
    }

    @Test
    fun `finishing setup changes nothing else about the settings`() {
        val before = AppSettings(
            uiAccent = UiAccent.Lime,
            streamPreset = StreamPreset.LowDataSaver,
            showStatsOnLaunch = false,
            showSessionReportAfterStream = false,
            analyticsConsentAsked = true,
            analyticsOptOut = false,
        )

        val after = before.completingSetupFlow(SetupStep.Ready)

        assertEquals(
            before,
            after.copy(setupFlowCompletedVersion = before.setupFlowCompletedVersion),
        )
    }
}
