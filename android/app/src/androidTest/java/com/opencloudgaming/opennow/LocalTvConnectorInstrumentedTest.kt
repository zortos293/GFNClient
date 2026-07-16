package com.opencloudgaming.opennow

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalTvConnectorInstrumentedTest {
    @Test
    fun encryptedLocalPairingTransfersLaunchAndSignIn() = runBlocking {
        val tv = LocalTvConnector()
        val phone = LocalTvConnector()
        try {
            tv.startHosting()
            val pairingState = awaitState(tv) { it.hosting && it.pairUri != null }
            assertTrue(pairingState.pairingCode.orEmpty().matches(Regex("[0-9]{4}")))
            val pairUri = pairingState.pairUri!!

            phone.pairPhone(Uri.parse(pairUri))
            val phoneState = awaitState(phone) { it.phoneConnected || it.error != null }
            assertTrue(phoneState.error.orEmpty(), phoneState.phoneConnected)
            val pairedState = awaitState(tv) { it.pairedDeviceName != null }
            assertTrue(pairedState.trustRequestedByDevice)

            val launchResult = async { withTimeout(5_000L) { tv.launchRequests.first() } }
            phone.sendLaunch("test-game-42", "Connector test game")
            assertEquals("test-game-42", launchResult.await().gameId)

            phone.sendRemoteAction("open_stream_menu")
            val untrustedState = awaitState(phone) { it.error?.contains("Trust this phone") == true }
            assertTrue(untrustedState.error.orEmpty(), untrustedState.error?.contains("Trust this phone") == true)

            tv.setPairedDeviceTrusted(true)
            val remoteResult = async { withTimeout(5_000L) { tv.remoteRequests.first() } }
            phone.sendRemoteAction("open_stream_menu")
            assertEquals("open_stream_menu", remoteResult.await().action)

            val transferred = AuthSession(
                provider = LoginProvider(
                    idpId = "test",
                    code = "TEST",
                    displayName = "Test provider",
                    streamingServiceUrl = "https://example.invalid",
                ),
                tokens = AuthTokens(
                    accessToken = "instrumentation-access-token",
                    refreshToken = "instrumentation-refresh-token",
                    expiresAt = System.currentTimeMillis() + 60_000L,
                ),
                user = AuthUser(
                    userId = "instrumentation-user",
                    displayName = "Instrumentation user",
                    membershipTier = "FREE",
                ),
            )
            val signInResult = async { withTimeout(5_000L) { tv.signInRequests.first() } }
            phone.sendSignIn(transferred)
            assertEquals(transferred, signInResult.await())
        } finally {
            phone.close()
            tv.close()
        }
    }

    private suspend fun awaitState(
        connector: LocalTvConnector,
        predicate: (LocalTvConnectorState) -> Boolean,
    ): LocalTvConnectorState = withTimeout(8_000L) {
        while (true) {
            connector.state.value.takeIf(predicate)?.let { return@withTimeout it }
            delay(25L)
        }
        @Suppress("UNREACHABLE_CODE")
        connector.state.value
    }
}
