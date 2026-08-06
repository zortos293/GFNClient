package com.opencloudgaming.opennow

import android.content.pm.ServiceInfo
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStreamKeepAliveNotifierTest {
    @Test
    fun addsMicrophoneForegroundTypeOnlyWhenCaptureIsActiveAndSupported() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            androidStreamForegroundServiceType(
                microphoneCaptureActive = false,
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            ),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            androidStreamForegroundServiceType(
                microphoneCaptureActive = true,
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
            ),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            androidStreamForegroundServiceType(
                microphoneCaptureActive = true,
                sdkInt = Build.VERSION_CODES.Q,
            ),
        )
    }

    @Test
    fun preparesMicrophoneServiceOnlyForReadyPermittedMicrophoneStream() {
        val readyMicrophoneState = OpenNowUiState(
            page = AppPage.Stream,
            streamStatus = "streaming",
            streamSession = readySession(),
            activeStreamSettings = StreamSettings(microphoneMode = MicrophoneMode.VoiceActivity),
        )

        assertTrue(shouldPrepareAndroidStreamMicrophone(readyMicrophoneState, permissionGranted = true))
        assertFalse(shouldPrepareAndroidStreamMicrophone(readyMicrophoneState, permissionGranted = false))
        assertFalse(
            shouldPrepareAndroidStreamMicrophone(
                readyMicrophoneState.copy(
                    activeStreamSettings = StreamSettings(microphoneMode = MicrophoneMode.Disabled),
                ),
                permissionGranted = true,
            ),
        )
        assertFalse(
            shouldPrepareAndroidStreamMicrophone(
                readyMicrophoneState.copy(page = AppPage.Home),
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun keepsReadyStreamAlive() {
        val state = OpenNowUiState(
            page = AppPage.Stream,
            streamStatus = "streaming",
            streamSession = readySession(),
        )

        assertTrue(shouldKeepAndroidStreamAlive(state))
    }

    @Test
    fun doesNotKeepQueueOrExitedStreamAlive() {
        assertFalse(
            shouldKeepAndroidStreamAlive(
                OpenNowUiState(page = AppPage.Stream, streamStatus = "queueing"),
            ),
        )
        assertFalse(
            shouldKeepAndroidStreamAlive(
                OpenNowUiState(page = AppPage.Home, streamStatus = "streaming", streamSession = readySession()),
            ),
        )
    }

    @Test
    fun capturesExactReadySessionForTaskRemovalCleanup() {
        val session = readySession()
        val settings = StreamSettings(resolution = "2560x1440", fps = 120)
        val request = activeStreamShutdownRequest(
            OpenNowUiState(
                page = AppPage.Stream,
                streamStatus = "streaming",
                streamSession = session,
                activeStreamSettings = settings,
            ),
        )

        assertNotNull(request)
        assertEquals(session, request?.session)
        assertEquals(settings, request?.settings)
        assertNull(
            activeStreamShutdownRequest(
                OpenNowUiState(
                    page = AppPage.Home,
                    streamStatus = "streaming",
                    streamSession = session,
                ),
            ),
        )
    }

    private fun readySession(): SessionInfo = SessionInfo(
        sessionId = "session-id",
        status = 2,
        serverIp = "example.invalid",
        signalingServer = "example.invalid",
        signalingUrl = "wss://example.invalid",
    )
}
