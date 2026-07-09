package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchErrorsTest {
    @Test
    fun limitedModeCloudMatchStatusUsesGameTitle() {
        val error = IllegalStateException(
            "CloudMatch returned status 81: STREAMING_NOT_ALLOWED_IN_LIMITED_MODE 8A91000D",
        )

        assertEquals(
            "Subnautica 2 is only available for Priority or Ultimate members",
            normalizeLaunchErrorMessage(error, "Subnautica 2"),
        )
    }

    @Test
    fun limitedModeCloudMatchStatusFallsBackWithoutGameTitle() {
        val error = IllegalStateException("CloudMatch returned status 81: STREAMING_NOT_ALLOWED_IN_LIMITED_MODE")

        assertEquals(
            "This game is only available for Priority or Ultimate members",
            normalizeLaunchErrorMessage(error),
        )
    }

    @Test
    fun maintenanceErrorsStillUseFriendlyCopy() {
        val error = IllegalStateException("Game server is under maintenance")

        assertEquals(
            "Game is patching or under maintenance. Try again when NVIDIA finishes updating it.",
            normalizeLaunchErrorMessage(error, "Subnautica 2"),
        )
    }
}
