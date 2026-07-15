package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAuthRefreshTest {
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
