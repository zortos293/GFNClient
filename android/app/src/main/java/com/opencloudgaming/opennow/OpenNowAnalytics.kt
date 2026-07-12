package com.opencloudgaming.opennow

import android.app.Application
import android.util.Log
import com.posthog.PostHog
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig

private const val ANALYTICS_LOG_TAG = "OpenNowAnalytics"
private const val ANALYTICS_FLUSH_INTERVAL_SECONDS = 10

internal object OpenNowAnalytics {
    fun setup(application: Application, settings: AppSettings) {
        val token = BuildConfig.POSTHOG_PROJECT_TOKEN.trim()
        if (token.isEmpty()) {
            Log.w(ANALYTICS_LOG_TAG, "PostHog disabled because no project token is configured.")
            return
        }

        val config = PostHogAndroidConfig(
            apiKey = token,
            host = BuildConfig.POSTHOG_HOST,
        ).apply { applyOpenNowSettings(settings) }

        runCatching {
            PostHogAndroid.setup(application, config)
            applyOptOut(!settings.analyticsSharingEnabled)
        }.onFailure { error ->
            Log.w(ANALYTICS_LOG_TAG, "PostHog setup failed.", error)
        }
    }

    fun applyOptOut(optedOut: Boolean) {
        runPostHogOperation("opt-out update") {
            if (optedOut) {
                PostHog.optOut()
            } else {
                PostHog.optIn()
            }
        }
    }

    fun capture(event: String, properties: Map<String, Any>? = null) {
        runPostHogOperation("capture") {
            PostHog.capture(
                event = event,
                properties = sanitizedAnalyticsProperties(properties),
            )
            flushReleaseQueue()
        }
    }

    fun reset() {
        runPostHogOperation("reset") {
            val optedOut = PostHog.isOptOut()
            PostHog.reset()
            applyOptOut(optedOut)
        }
    }

    private fun flushReleaseQueue() {
        if (BuildConfig.DEBUG) return
        PostHog.flush()
    }

    private inline fun runPostHogOperation(operation: String, block: () -> Unit) {
        runCatching(block).onFailure { error ->
            Log.w(ANALYTICS_LOG_TAG, "PostHog $operation failed.", error)
        }
    }
}

internal fun PostHogAndroidConfig.applyOpenNowSettings(settings: AppSettings) {
    optOut = !settings.analyticsSharingEnabled
    captureApplicationLifecycleEvents = true
    captureDeepLinks = false
    captureScreenViews = true
    flushIntervalSeconds = ANALYTICS_FLUSH_INTERVAL_SECONDS
    sessionReplay = false
    sessionReplayConfig.apply {
        maskAllTextInputs = true
        maskAllImages = true
        screenshot = false
        captureLogcat = false
    }
    errorTrackingConfig.autoCapture = true
    addBeforeSend { event ->
        event.copy(
            properties = sanitizedAnalyticsProperties(
                properties = event.properties,
                redactExceptionText = event.event == "\$exception",
            ).toMutableMap(),
        )
    }
}

internal fun sanitizedAnalyticsProperties(
    properties: Map<String, Any>?,
    redactExceptionText: Boolean = false,
): Map<String, Any> =
    buildMap {
        put("\$geoip_disable", true)
        properties.orEmpty().forEach { (key, value) ->
            if (!isSensitiveAnalyticsProperty(key, redactExceptionText)) {
                put(key, sanitizeAnalyticsValue(value, redactExceptionText))
            }
        }
    }

private fun sanitizeAnalyticsValue(value: Any, redactExceptionText: Boolean): Any =
    when (value) {
        is String -> sanitizeDiagnosticExport(value).take(500)
        is Map<*, *> -> value.entries
            .mapNotNull { (key, nestedValue) ->
                val stringKey = key as? String ?: return@mapNotNull null
                val presentValue = nestedValue ?: return@mapNotNull null
                stringKey.takeUnless { isSensitiveAnalyticsProperty(it, redactExceptionText) }
                    ?.let { it to sanitizeAnalyticsValue(presentValue, redactExceptionText) }
            }
            .toMap()
        is Iterable<*> -> value.mapNotNull { it?.let { item -> sanitizeAnalyticsValue(item, redactExceptionText) } }
        is Array<*> -> value.mapNotNull { it?.let { item -> sanitizeAnalyticsValue(item, redactExceptionText) } }
        else -> value
    }

private fun isSensitiveAnalyticsProperty(key: String, redactExceptionText: Boolean): Boolean {
    val normalized = key.lowercase().filter(Char::isLetterOrDigit)
    return (redactExceptionText && normalized in setOf("message", "value", "exceptionmessage")) || normalized in setOf(
        "authorization",
        "credential",
        "cookie",
        "displayname",
        "email",
        "errormessage",
        "password",
        "query",
        "searchquery",
        "secret",
        "token",
        "userid",
        "username",
    ) || normalized.endsWith("token") || normalized.endsWith("credential")
}
