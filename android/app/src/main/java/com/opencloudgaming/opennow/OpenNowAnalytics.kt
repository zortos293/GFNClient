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
            applyOptOut(settings.analyticsOptOut)
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

    fun identify(session: AuthSession) {
        runPostHogOperation("identify") {
            PostHog.identify(
                distinctId = session.user.userId,
                userProperties = mapOf(
                    "display_name" to session.user.displayName,
                    "membership_tier" to session.user.membershipTier,
                    "provider" to session.provider.code,
                ),
            )
            flushReleaseQueue()
        }
    }

    fun capture(event: String, properties: Map<String, Any>? = null) {
        runPostHogOperation("capture") {
            PostHog.capture(
                event = event,
                properties = properties,
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
    optOut = settings.analyticsOptOut
    captureApplicationLifecycleEvents = true
    captureDeepLinks = true
    captureScreenViews = true
    flushIntervalSeconds = ANALYTICS_FLUSH_INTERVAL_SECONDS
    sessionReplay = true
    sessionReplayConfig.apply {
        maskAllTextInputs = true
        maskAllImages = true
        screenshot = true
    }
    errorTrackingConfig.autoCapture = true
}
