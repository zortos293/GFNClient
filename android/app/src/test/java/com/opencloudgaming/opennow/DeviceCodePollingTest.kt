package com.opencloudgaming.opennow

import java.net.ConnectException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceCodePollingTest {
    @Test
    fun dnsAndConnectionFailuresRecoverWithoutReplacingTheChallenge() = runBlocking {
        val clock = PollClock()
        val failures = listOf(
            UnknownHostException("login.nvidia.com"),
            ConnectException("Network unreachable"),
            SocketTimeoutException("Timed out"),
        )
        var attempts = 0
        val response = clock.poll {
            val attempt = attempts++
            if (attempt < failures.size) throw failures[attempt]
            200 to "token response"
        }

        assertEquals(200 to "token response", response)
        assertEquals(4, attempts)
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L), clock.waits)
    }

    @Test
    fun outageRetriesAreCappedAndStopAtCodeExpiry() = runBlocking {
        val clock = PollClock(expiresAt = 200_000L)
        val failure = UnknownHostException("login.nvidia.com")
        var attempts = 0
        val thrown = runCatching {
            clock.poll {
                attempts++
                throw failure
            }
        }.exceptionOrNull()

        assertEquals("Device sign-in code expired.", thrown?.message)
        assertSame(failure, thrown?.cause)
        assertEquals(6, attempts)
        assertEquals(listOf(5_000L, 10_000L, 20_000L, 40_000L, 60_000L, 60_000L, 5_000L), clock.waits)
    }

    @Test
    fun doesNotSendPollWhenCodeExpiresDuringWait() = runBlocking {
        val clock = PollClock(expiresAt = 3_000L)
        val thrown = runCatching {
            clock.poll { error("Must not send an expired code") }
        }.exceptionOrNull()

        assertEquals("Device sign-in code expired.", thrown?.message)
        assertEquals(listOf(3_000L), clock.waits)
    }

    @Test
    fun respectsServerPollIntervalEvenAboveBackoffCap() = runBlocking {
        val clock = PollClock()
        var attempts = 0
        clock.poll(intervalSeconds = 90) {
            if (attempts++ == 0) throw SocketTimeoutException()
            200 to "token response"
        }

        assertEquals(listOf(90_000L, 180_000L), clock.waits)
    }

    @Test
    fun returnsOAuthAndHttpErrorsToCallerWithoutRetryingThem() = runBlocking {
        for (response in listOf(
            400 to "{\"error\":\"authorization_pending\"}",
            400 to "{\"error\":\"slow_down\"}",
            400 to "{\"error\":\"access_denied\"}",
            400 to "{\"error\":\"expired_token\"}",
            503 to "Service unavailable",
        )) {
            val clock = PollClock()
            assertEquals(response, clock.poll { response })
            assertEquals(listOf(5_000L), clock.waits)
        }
    }

    @Test
    fun certificateProtocolAndCancellationFailuresPropagateImmediately() = runBlocking {
        for (failure in listOf(
            SSLHandshakeException("Untrusted certificate"),
            ProtocolException("Invalid response"),
            CancellationException("User cancelled sign-in"),
        )) {
            val clock = PollClock()
            val thrown = runCatching { clock.poll { throw failure } }.exceptionOrNull()
            assertSame(failure, thrown)
            assertEquals(listOf(5_000L), clock.waits)
        }
    }

    @Test
    fun userCanCancelWhileWaitingToRetry() = runBlocking {
        val retryStarted = CompletableDeferred<Unit>()
        var attempts = 0
        var waits = 0
        val job = launch {
            awaitDeviceCodePollResponse(
                expiresAt = 900_000L,
                intervalSeconds = 5,
                nowMs = { 0L },
                wait = {
                    if (waits++ > 0) {
                        retryStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
            ) {
                attempts++
                throw UnknownHostException()
            }
        }
        retryStarted.await()
        job.cancelAndJoin()

        assertEquals(1, attempts)
    }

    private class PollClock(val expiresAt: Long = 900_000L) {
        var nowMs = 0L
        val waits = mutableListOf<Long>()

        suspend fun poll(
            intervalSeconds: Int = 5,
            request: suspend () -> Pair<Int, String>,
        ): Pair<Int, String> = awaitDeviceCodePollResponse(
            expiresAt = expiresAt,
            intervalSeconds = intervalSeconds,
            nowMs = { nowMs },
            wait = { waits += it; nowMs += it },
            request = request,
        )
    }
}
