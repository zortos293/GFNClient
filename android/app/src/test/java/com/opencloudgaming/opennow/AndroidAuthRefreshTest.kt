package com.opencloudgaming.opennow

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAuthRefreshTest {
    @Test
    fun legacySessionTriesBothKnownAuthClients() {
        assertEquals(
            listOf("browser-client", "device-client"),
            authenticationRefreshClientIds(
                savedClientId = null,
                browserClientId = "browser-client",
                deviceClientId = "device-client",
            ),
        )
    }

    @Test
    fun savedAuthClientIsTriedFirstWithoutDuplicates() {
        assertEquals(
            listOf("device-client", "browser-client"),
            authenticationRefreshClientIds(
                savedClientId = "device-client",
                browserClientId = "browser-client",
                deviceClientId = "device-client",
            ),
        )
    }

    @Test
    fun freshAccessAndClientTokensDoNotNeedBackgroundRefresh() {
        val now = 1_000_000L
        val tokens = tokens(
            expiresAt = now + TOKEN_REFRESH_WINDOW_MS + 1L,
            clientTokenExpiresAt = now + CLIENT_TOKEN_REFRESH_WINDOW_MS + 1L,
        )

        assertFalse(tokens.needsBackgroundRefresh(now))
    }

    @Test
    fun accessTokenInsideRefreshWindowNeedsBackgroundRefresh() {
        val now = 1_000_000L
        val tokens = tokens(
            expiresAt = now + TOKEN_REFRESH_WINDOW_MS - 1L,
            clientTokenExpiresAt = now + CLIENT_TOKEN_REFRESH_WINDOW_MS + 1L,
        )

        assertTrue(tokens.needsBackgroundRefresh(now))
    }

    @Test
    fun missingOrExpiringClientTokenNeedsBackgroundRefresh() {
        val now = 1_000_000L
        val missingClientToken = tokens(
            expiresAt = now + TOKEN_REFRESH_WINDOW_MS + 1L,
            clientToken = null,
            clientTokenExpiresAt = null,
        )
        val expiringClientToken = tokens(
            expiresAt = now + TOKEN_REFRESH_WINDOW_MS + 1L,
            clientTokenExpiresAt = now + CLIENT_TOKEN_REFRESH_WINDOW_MS - 1L,
        )

        assertTrue(missingClientToken.needsBackgroundRefresh(now))
        assertTrue(expiringClientToken.needsBackgroundRefresh(now))
    }

    @Test
    fun bestEffortRefreshFallsBackWhenRefreshFails() = runBlocking {
        val fallback = session("fallback-token")
        var reported: Throwable? = null

        val result = refreshedSessionOrFallback(
            fallback = fallback,
            refresh = { error("Token refresh failed") },
            onFailure = { reported = it },
        )

        assertSame(fallback, result)
        assertEquals("Token refresh failed", reported?.message)
    }

    @Test
    fun bestEffortRefreshStillPropagatesCancellation() = runBlocking {
        val cancellation = CancellationException("cancelled")

        val thrown = runCatching {
            refreshedSessionOrFallback(
                fallback = session("fallback-token"),
                refresh = { throw cancellation },
            )
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    private fun session(accessToken: String): AuthSession = AuthSession(
        provider = LoginProvider(
            idpId = "test-idp",
            code = "TEST",
            displayName = "Test",
            streamingServiceUrl = "https://example.invalid",
        ),
        tokens = AuthTokens(
            accessToken = accessToken,
            expiresAt = 0L,
        ),
        user = AuthUser(
            userId = "test-user",
            displayName = "Test user",
            membershipTier = "FREE",
        ),
    )

    private fun tokens(
        expiresAt: Long,
        clientToken: String? = "client-token",
        clientTokenExpiresAt: Long?,
    ): AuthTokens = AuthTokens(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        idToken = "id-token",
        expiresAt = expiresAt,
        clientToken = clientToken,
        clientTokenExpiresAt = clientTokenExpiresAt,
    )
}
