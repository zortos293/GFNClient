package com.opencloudgaming.opennow

import com.posthog.PostHogEvent
import com.posthog.android.PostHogAndroidConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenNowAnalyticsTest {
    @Test
    fun appliesPrivacyFirstAnalyticsConfig() {
        val config = PostHogAndroidConfig(
            apiKey = "phc_test",
            host = "https://us.i.posthog.com",
        ).apply {
            sessionReplay = false
            sessionReplayConfig.screenshot = false
            sessionReplayConfig.maskAllTextInputs = false
            sessionReplayConfig.maskAllImages = false
            sessionReplayConfig.captureLogcat = true
            captureDeepLinks = true
        }

        config.applyOpenNowSettings(
            AppSettings(
                analyticsConsentAsked = true,
                analyticsOptOut = false,
            ),
        )

        assertFalse(config.optOut)
        assertTrue(config.captureApplicationLifecycleEvents)
        assertFalse(config.captureDeepLinks)
        assertTrue(config.captureScreenViews)
        assertEquals(10, config.flushIntervalSeconds)
        assertFalse(config.sessionReplay)
        assertFalse(config.sessionReplayConfig.screenshot)
        assertFalse(config.sessionReplayConfig.captureLogcat)
        assertTrue(config.sessionReplayConfig.maskAllTextInputs)
        assertTrue(config.sessionReplayConfig.maskAllImages)
        assertTrue(config.errorTrackingConfig.autoCapture)
        assertEquals(1, config.beforeSendList.size)

        val sanitizedCrash = config.beforeSendList.single().run(
            PostHogEvent(
                event = "\$exception",
                distinctId = "anonymous",
                properties = mutableMapOf(
                    "\$exception_list" to listOf(
                        mapOf(
                            "type" to "IllegalStateException",
                            "value" to "token=secret for player@example.invalid",
                        ),
                    ),
                ),
            ),
        )
        val exception = (sanitizedCrash?.properties?.get("\$exception_list") as List<*>).single() as Map<*, *>
        assertEquals("IllegalStateException", exception["type"])
        assertFalse(exception.containsKey("value"))
        assertEquals(true, sanitizedCrash.properties?.get("\$geoip_disable"))
    }

    @Test
    fun analyticsStayOffUntilConsentIsRecorded() {
        val config = PostHogAndroidConfig(
            apiKey = "phc_test",
            host = "https://us.i.posthog.com",
        )

        config.applyOpenNowSettings(AppSettings(analyticsOptOut = false, analyticsConsentAsked = false))

        assertTrue(config.optOut)
    }

    @Test
    fun analyticsPropertiesDropFreeFormAndIdentifyingValues() {
        val properties = sanitizedAnalyticsProperties(
            mapOf(
                "query" to "private search",
                "error_message" to "token=secret from player@example.invalid",
                "provider" to "NP-PCC",
                "server" to "203.0.113.42",
                "metadata" to "sessionId=private deviceName=Kiefers-Controller email=player@example.invalid",
            ),
        )

        assertFalse(properties.containsKey("query"))
        assertFalse(properties.containsKey("error_message"))
        assertEquals("NP-PCC", properties["provider"])
        assertEquals("[redacted-ip]", properties["server"])
        val metadata = properties["metadata"] as String
        assertFalse(metadata.contains("private"))
        assertFalse(metadata.contains("Kiefers-Controller"))
        assertFalse(metadata.contains("player@example.invalid"))
        assertEquals(true, properties["\$geoip_disable"])
    }
}
