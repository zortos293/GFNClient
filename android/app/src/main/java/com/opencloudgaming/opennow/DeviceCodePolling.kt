package com.opencloudgaming.opennow

import java.io.IOException
import java.net.ProtocolException
import javax.net.ssl.SSLException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Keeps the current sign-in code alive across temporary transport failures. */
internal suspend fun awaitDeviceCodePollResponse(
    expiresAt: Long,
    intervalSeconds: Int,
    nowMs: () -> Long = System::currentTimeMillis,
    wait: suspend (Long) -> Unit = { delay(it) },
    request: suspend () -> Pair<Int, String>,
): Pair<Int, String> {
    var delayMs = intervalSeconds.coerceAtLeast(5) * 1000L
    val maxDelayMs = maxOf(delayMs * 2L, 60_000L)
    var lastNetworkError: IOException? = null
    while (true) {
        currentCoroutineContext().ensureActive()
        val remainingMs = expiresAt - nowMs()
        if (remainingMs <= 0L) {
            throw IllegalStateException("Device sign-in code expired.", lastNetworkError)
        }
        wait(minOf(delayMs, remainingMs))
        currentCoroutineContext().ensureActive()
        if (nowMs() >= expiresAt) {
            throw IllegalStateException("Device sign-in code expired.", lastNetworkError)
        }
        try {
            // Return every HTTP response so the caller still handles OAuth denial,
            // expiration, and slow_down without retrying terminal server errors.
            return request()
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            if (error is SSLException || error is ProtocolException) throw error
            lastNetworkError = error
            // RFC 8628 section 3.5 requires reduced polling after connection timeouts.
            delayMs = (delayMs * 2L).coerceAtMost(maxDelayMs)
        }
    }
}
