package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneSupportTest {
    @Test
    fun microphoneCaptureRequiresEnabledModeAndRuntimePermission() {
        assertTrue(
            shouldCaptureMicrophone(
                mode = MicrophoneMode.VoiceActivity,
                permissionGranted = true,
            ),
        )
        assertTrue(
            shouldCaptureMicrophone(
                mode = MicrophoneMode.PushToTalk,
                permissionGranted = true,
            ),
        )
        assertFalse(
            shouldCaptureMicrophone(
                mode = MicrophoneMode.Disabled,
                permissionGranted = true,
            ),
        )
        assertFalse(
            shouldCaptureMicrophone(
                mode = MicrophoneMode.VoiceActivity,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun videoPresetChangesPreserveMicrophonePreferences() {
        val source = StreamSettings(
            microphoneMode = MicrophoneMode.VoiceActivity,
            microphoneDeviceId = "preferred-device",
        )

        val updated = StreamSettings(resolution = "1280x720")
            .withMicrophoneSettingsFrom(source)

        assertTrue(updated.microphoneMode == MicrophoneMode.VoiceActivity)
        assertTrue(updated.microphoneDeviceId == "preferred-device")
    }
}
