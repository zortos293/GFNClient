package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStreamOrientationTest {
    @Test
    fun locksPhoneLandscapeOnlyAfterQueueCompletes() {
        val readySession = readySession()

        assertFalse(
            shouldLockPhoneStreamLandscape(
                state = OpenNowUiState(
                    page = AppPage.Stream,
                    streamStatus = "queue",
                    streamSession = readySession,
                ),
                smallestScreenWidthDp = 390,
            ),
        )
        assertTrue(
            shouldLockPhoneStreamLandscape(
                state = OpenNowUiState(
                    page = AppPage.Stream,
                    streamStatus = "connecting",
                    streamSession = readySession,
                ),
                smallestScreenWidthDp = 390,
            ),
        )
    }

    @Test
    fun keepsPhonesUnlockedUntilSessionIsStreamReady() {
        assertFalse(
            shouldLockPhoneStreamLandscape(
                state = OpenNowUiState(
                    page = AppPage.Stream,
                    streamStatus = "connecting",
                    streamSession = readySession(status = 1),
                ),
                smallestScreenWidthDp = 390,
            ),
        )
    }

    @Test
    fun doesNotLockTabletOrTvLandscape() {
        val state = OpenNowUiState(
            page = AppPage.Stream,
            streamStatus = "streaming",
            streamSession = readySession(),
        )

        assertFalse(shouldLockPhoneStreamLandscape(state, smallestScreenWidthDp = 600))
        assertFalse(
            shouldLockPhoneStreamLandscape(
                state = state.copy(codecReport = runtimeCodecReport(androidTvProfile = true)),
                smallestScreenWidthDp = 390,
            ),
        )
    }

    private fun readySession(status: Int = 2): SessionInfo =
        SessionInfo(
            sessionId = "session",
            status = status,
            serverIp = "203.0.113.10",
            signalingServer = "signal.example.com",
            signalingUrl = "wss://signal.example.com",
        )

    private fun runtimeCodecReport(androidTvProfile: Boolean): RuntimeCodecReport =
        RuntimeCodecReport(
            capabilities = emptyList(),
            nativeRuntimeSummary = "",
            androidTvProfile = androidTvProfile,
            lowPowerGpuProfile = false,
        )
}
