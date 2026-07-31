package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchErrorsTest {
    @Test
    fun freeTierEntitlementFailureExplainsMembershipRequirement() {
        val error = CloudMatchRequestStatusException(
            statusCode = 18,
            statusDescription = "ENTITLEMENT_FAILURE_STATUS 8A910006",
            unifiedErrorCode = "-1970208762",
        )

        assertEquals(
            "Your GeForce NOW account is on the Free tier. This game requires a Priority or Ultimate membership.",
            normalizeLaunchErrorMessage(error, "Subnautica 2"),
        )
    }

    @Test
    fun limitedModeCloudMatchStatusUsesGameTitle() {
        val error = CloudMatchRequestStatusException(
            statusCode = 81,
            statusDescription = "STREAMING_NOT_ALLOWED_IN_LIMITED_MODE 8A91000D",
            unifiedErrorCode = "-1970208755",
        )

        assertEquals(
            "Subnautica 2 is only available for Priority or Ultimate members",
            normalizeLaunchErrorMessage(error, "Subnautica 2"),
        )
    }

    @Test
    fun limitedModeCloudMatchStatusFallsBackWithoutGameTitle() {
        val error = CloudMatchRequestStatusException(
            statusCode = 81,
            statusDescription = "STREAMING_NOT_ALLOWED_IN_LIMITED_MODE",
            unifiedErrorCode = null,
        )

        assertEquals(
            "This game is only available for Priority or Ultimate members",
            normalizeLaunchErrorMessage(error),
        )
    }

    @Test
    fun unrelatedCloudMatchFailureKeepsItsOwnMessage() {
        val error = CloudMatchRequestStatusException(
            statusCode = 42,
            statusDescription = "CAPACITY_FAILURE_STATUS",
            unifiedErrorCode = "DEADBEEF",
        )

        assertEquals(
            "CloudMatch returned status 42: CAPACITY_FAILURE_STATUS (unified error DEADBEEF)",
            normalizeLaunchErrorMessage(error, "Subnautica 2"),
        )
    }

    @Test
    fun entitlementWordsInsideAnUnstructuredErrorAreNotMisclassified() {
        val error = IllegalStateException(
            "Diagnostics mentioned ENTITLEMENT_FAILURE_STATUS, but DNS lookup failed",
        )

        assertEquals(
            "Diagnostics mentioned ENTITLEMENT_FAILURE_STATUS, but DNS lookup failed",
            normalizeLaunchErrorMessage(error, "Subnautica 2"),
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
