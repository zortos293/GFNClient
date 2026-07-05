package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLaunchIntentTest {
    @Test
    fun extractsLaunchIdFromCustomSchemePath() {
        val id = externalLaunchIdFromParts(
            extras = emptyList(),
            scheme = "opennow",
            host = "launch",
            pathSegments = listOf("100362311"),
            schemeSpecificPart = "//launch/100362311",
            queryParameters = emptyMap(),
        )

        assertEquals("100362311", id)
    }

    @Test
    fun extractsLaunchIdFromOpaqueCustomScheme() {
        val id = externalLaunchIdFromParts(
            extras = emptyList(),
            scheme = "opennow",
            host = null,
            pathSegments = emptyList(),
            schemeSpecificPart = "launch/100362311",
            queryParameters = emptyMap(),
        )

        assertEquals("100362311", id)
    }

    @Test
    fun queryParameterBeatsPathFallback() {
        val id = externalLaunchIdFromParts(
            extras = emptyList(),
            scheme = "opennow",
            host = "launch",
            pathSegments = listOf("wrong"),
            schemeSpecificPart = "//launch/wrong",
            queryParameters = mapOf("appId" to "100362311"),
        )

        assertEquals("100362311", id)
    }

    @Test
    fun intentExtraBeatsUri() {
        val id = externalLaunchIdFromParts(
            extras = listOf("100362311"),
            scheme = "opennow",
            host = "launch",
            pathSegments = listOf("wrong"),
            schemeSpecificPart = "//launch/wrong",
            queryParameters = emptyMap(),
        )

        assertEquals("100362311", id)
    }

    @Test
    fun ignoresNonOpenNowUriWithoutExtra() {
        val id = externalLaunchIdFromParts(
            extras = emptyList(),
            scheme = "https",
            host = "example.com",
            pathSegments = listOf("launch", "100362311"),
            schemeSpecificPart = "//example.com/launch/100362311",
            queryParameters = mapOf("appId" to "100362311"),
        )

        assertNull(id)
    }
}
