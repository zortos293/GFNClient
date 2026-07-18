package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualTokenSignInTest {
    @Test
    fun acceptsRawBearerAccessToken() {
        val tokens = parseManualAuthTokens("  Bearer access-value  ", currentTimeMs = 1_000L)

        assertEquals("access-value", tokens.accessToken)
        assertEquals(86_401_000L, tokens.expiresAt)
        assertNull(tokens.refreshToken)
    }

    @Test
    fun acceptsOAuthTokenResponseJson() {
        val tokens = parseManualAuthTokens(
            """
                {
                  "access_token": "access-value",
                  "refresh_token": "refresh-value",
                  "id_token": "id-value",
                  "client_token": "client-value",
                  "expires_in": 3600
                }
            """.trimIndent(),
            currentTimeMs = 10_000L,
        )

        assertEquals("access-value", tokens.accessToken)
        assertEquals("refresh-value", tokens.refreshToken)
        assertEquals("id-value", tokens.idToken)
        assertEquals("client-value", tokens.clientToken)
        assertEquals(3_610_000L, tokens.expiresAt)
    }

    @Test
    fun acceptsPersistedSessionTokenShape() {
        val tokens = parseManualAuthTokens(
            """
                {
                  "tokens": {
                    "accessToken": "access-value",
                    "refreshToken": "refresh-value",
                    "expiresAt": 2000000000,
                    "authClientId": "saved-client"
                  }
                }
            """.trimIndent(),
            currentTimeMs = 10_000L,
        )

        assertEquals("access-value", tokens.accessToken)
        assertEquals("refresh-value", tokens.refreshToken)
        assertEquals(2_000_000_000_000L, tokens.expiresAt)
        assertEquals("saved-client", tokens.authClientId)
    }
}
