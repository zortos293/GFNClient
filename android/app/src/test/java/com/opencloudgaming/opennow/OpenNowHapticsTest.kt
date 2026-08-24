package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenNowHapticsTest {
    @Test
    fun recognizesBuiltInControllerAndroidHandhelds() {
        assertTrue(
            isGamingHandheldDevice(
                manufacturer = "AYN",
                brand = "AYN",
                model = "Odin 2 Portal",
                device = "odin2portal",
                product = "odin2portal",
            ),
        )
        assertTrue(
            isGamingHandheldDevice(
                manufacturer = "Moorechip",
                brand = "Retroid Pocket",
                model = "Retroid Pocket 5",
                device = "RP5",
                product = "RP5",
            ),
        )
        assertTrue(
            isGamingHandheldDevice(
                manufacturer = "Anbernic",
                brand = "Anbernic",
                model = "RG556",
                device = "RG556",
                product = "RG556",
            ),
        )
    }

    @Test
    fun ordinaryPhonesDoNotEnableHandheldNavigationHaptics() {
        assertFalse(
            isGamingHandheldDevice(
                manufacturer = "Google",
                brand = "google",
                model = "Pixel 10 Pro",
                device = "mustang",
                product = "mustang",
            ),
        )
        assertFalse(
            isGamingHandheldDevice(
                manufacturer = "Samsung",
                brand = "samsung",
                model = "SM-S938W",
                device = "pa3q",
                product = "pa3qcsx",
            ),
        )
    }

    @Test
    fun focusTicksAreLighterThanActivations() {
        val focus = hapticPulseFor(HapticCue.FocusMove)
        val activate = hapticPulseFor(HapticCue.Activate)
        assertTrue(focus.amplitude < activate.amplitude)
        assertTrue(focus.durationMs < activate.durationMs)
    }

    @Test
    fun everyCueRequestsAPlayableAmplitude() {
        HapticCue.entries.forEach { cue ->
            val pulse = hapticPulseFor(cue)
            assertTrue("$cue amplitude", pulse.amplitude in 1..255)
            assertTrue("$cue duration", pulse.durationMs > 0)
        }
    }

    @Test
    fun handheldPulsesAreLongerAndStrongerThanGenericPulses() {
        HapticCue.entries.forEach { cue ->
            val generic = hapticPulseFor(cue)
            val handheld = handheldHapticPulseFor(cue)
            assertTrue("$cue duration", handheld.durationMs > generic.durationMs)
            assertTrue("$cue amplitude", handheld.amplitude >= generic.amplitude)
        }
        assertEquals(255, handheldHapticPulseFor(HapticCue.FocusMove).amplitude)
    }

    @Test
    fun firstFocusTickAlwaysFires() {
        assertTrue(shouldEmitFocusHaptic(lastAtMs = 0L, nowMs = 0L))
    }

    @Test
    fun repeatedFocusMovesAreThrottled() {
        assertFalse(shouldEmitFocusHaptic(lastAtMs = 1_000L, nowMs = 1_000L + FOCUS_HAPTIC_MIN_INTERVAL_MS - 1))
        assertTrue(shouldEmitFocusHaptic(lastAtMs = 1_000L, nowMs = 1_000L + FOCUS_HAPTIC_MIN_INTERVAL_MS))
    }

    @Test
    fun forcedOutputDoesNotFallBackToTheOtherDevice() {
        // The whole point of forcing is that the other output is the one being avoided.
        assertEquals(
            HapticsOutputTarget.None,
            selectHapticsOutputTarget(
                vibrationEnabled = true,
                controllerRumbleAvailable = false,
                deviceHapticsAvailable = true,
                preference = HapticsOutputPreference.Controller,
            ),
        )
        assertEquals(
            HapticsOutputTarget.Device,
            selectHapticsOutputTarget(
                vibrationEnabled = true,
                controllerRumbleAvailable = true,
                deviceHapticsAvailable = true,
                preference = HapticsOutputPreference.Device,
            ),
        )
    }

    @Test
    fun forcedOutputStillObeysTheVibrationSwitch() {
        HapticsOutputPreference.entries.forEach { preference ->
            assertEquals(
                "$preference",
                HapticsOutputTarget.None,
                selectHapticsOutputTarget(
                    vibrationEnabled = false,
                    controllerRumbleAvailable = true,
                    deviceHapticsAvailable = true,
                    preference = preference,
                ),
            )
        }
    }
}
