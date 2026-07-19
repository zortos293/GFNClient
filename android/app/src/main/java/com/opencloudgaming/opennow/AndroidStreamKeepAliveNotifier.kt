package com.opencloudgaming.opennow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

private const val STREAM_CHANNEL_ID = "opennow_active_stream"
private const val STREAM_NOTIFICATION_ID = 4211
private const val STREAM_SERVICE_ACTION_START = "com.opencloudgaming.opennow.stream.START"
private const val STREAM_SERVICE_ACTION_STOP = "com.opencloudgaming.opennow.stream.STOP"
private const val STREAM_SERVICE_EXTRA_TITLE = "title"
private const val STREAM_SERVICE_EXTRA_SHUTDOWN_REQUEST = "shutdown_request"
private const val STREAM_SERVICE_TAG = "OpenNOWStreamService"
private const val STREAM_TASK_REMOVAL_TIMEOUT_MS = 15_000L

internal fun shouldKeepAndroidStreamAlive(state: OpenNowUiState): Boolean =
    state.page == AppPage.Stream &&
        state.streamStatus != "idle" &&
        state.streamSession?.isReadyForStream() == true

@Serializable
internal data class ActiveStreamShutdownRequest(
    val session: SessionInfo,
    val settings: StreamSettings,
)

internal fun activeStreamShutdownRequest(state: OpenNowUiState): ActiveStreamShutdownRequest? {
    if (!shouldKeepAndroidStreamAlive(state)) return null
    val session = state.streamSession ?: return null
    return ActiveStreamShutdownRequest(
        session = session,
        settings = state.activeStreamSettings ?: state.settings.stream,
    )
}

class AndroidStreamKeepAliveNotifier(context: Context) {
    private val appContext = context.applicationContext
    private var serviceStartRequested = false
    private var activeTitle: String? = null
    private var activeShutdownRequestJson: String? = null
    private var cancellationApplied = false

    fun update(state: OpenNowUiState) {
        if (!shouldKeepAndroidStreamAlive(state)) {
            cancel()
            return
        }
        cancellationApplied = false

        val title = state.streamGame?.title ?: "OpenNOW"
        val shutdownRequestJson = activeStreamShutdownRequest(state)?.let { request ->
            OpenNowJson.encodeToString(request)
        }
        if (
            serviceStartRequested &&
            activeTitle == title &&
            activeShutdownRequestJson == shutdownRequestJson
        ) return
        val intent = Intent(appContext, AndroidStreamKeepAliveService::class.java).apply {
            action = STREAM_SERVICE_ACTION_START
            putExtra(STREAM_SERVICE_EXTRA_TITLE, title)
            putExtra(STREAM_SERVICE_EXTRA_SHUTDOWN_REQUEST, shutdownRequestJson)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            serviceStartRequested = true
            activeTitle = title
            activeShutdownRequestJson = shutdownRequestJson
        }.onFailure { error ->
            Log.w(STREAM_SERVICE_TAG, "Unable to start stream foreground service", error)
        }
    }

    fun cancel() {
        if (!serviceStartRequested && cancellationApplied) return
        val intent = Intent(appContext, AndroidStreamKeepAliveService::class.java).apply {
            action = STREAM_SERVICE_ACTION_STOP
        }
        runCatching {
            if (serviceStartRequested) {
                appContext.startService(intent)
            } else {
                appContext.stopService(intent)
            }
        }.onFailure { error ->
            Log.w(STREAM_SERVICE_TAG, "Unable to stop stream foreground service", error)
        }
        serviceStartRequested = false
        activeTitle = null
        activeShutdownRequestJson = null
        cancellationApplied = true
        appContext.getSystemService(NotificationManager::class.java).cancel(STREAM_NOTIFICATION_ID)
    }
}

class AndroidStreamKeepAliveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var streamWakeLock: PowerManager.WakeLock? = null
    private var activeShutdownRequest: ActiveStreamShutdownRequest? = null
    private var taskRemovalCleanupJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            when (intent?.action) {
                STREAM_SERVICE_ACTION_STOP -> {
                    releaseStreamWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
                STREAM_SERVICE_ACTION_START, null -> {
                    intent?.getStringExtra(STREAM_SERVICE_EXTRA_SHUTDOWN_REQUEST)
                        ?.let { encoded ->
                            runCatching { OpenNowJson.decodeFromString<ActiveStreamShutdownRequest>(encoded) }
                                .onSuccess { activeShutdownRequest = it }
                                .onFailure { error ->
                                    Log.w(STREAM_SERVICE_TAG, "Unable to read active stream shutdown request", error)
                                }
                        }
                    startStreamForeground(intent?.getStringExtra(STREAM_SERVICE_EXTRA_TITLE) ?: "OpenNOW")
                }
                else -> {
                    releaseStreamWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }.onFailure { error ->
            Log.e(STREAM_SERVICE_TAG, "Stream foreground service failed", error)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val shutdownRequest = activeShutdownRequest
        if (shutdownRequest != null && taskRemovalCleanupJob?.isActive != true) {
            taskRemovalCleanupJob = serviceScope.launch {
                terminateCloudSessionAfterTaskRemoval(shutdownRequest)
                withContext(Dispatchers.Main.immediate) {
                    releaseStreamWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        } else if (shutdownRequest == null) {
            releaseStreamWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        releaseStreamWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun terminateCloudSessionAfterTaskRemoval(request: ActiveStreamShutdownRequest) {
        runCatching {
            withTimeout(STREAM_TASK_REMOVAL_TIMEOUT_MS) {
                val openNowApplication = application as OpenNowApplication
                val auth = openNowApplication.authRepository.restore(
                    forceRefresh = false,
                    throwOnRefreshFailure = false,
                    removeExpiredSessionOnFailure = false,
                ) ?: error("No signed-in account is available to stop the stream")
                GfnSessionRepository(
                    authStore = openNowApplication.authStore,
                    http = openNowApplication.httpClient,
                ).stopSession(
                    token = auth.tokens.idToken ?: auth.tokens.accessToken,
                    input = request.session,
                    settings = request.settings,
                )
            }
        }.onSuccess {
            SessionTimerAnchorStore(this).clear(request.session.sessionId)
            Log.i(STREAM_SERVICE_TAG, "Stopped cloud session after app task removal")
        }.onFailure { error ->
            Log.w(STREAM_SERVICE_TAG, "Unable to stop cloud session after app task removal", error)
        }
    }

    private fun startStreamForeground(title: String) {
        ensureStreamNotificationChannel(this)
        acquireStreamWakeLock()
        val notification = buildStreamNotification(this, title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(STREAM_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(STREAM_NOTIFICATION_ID, notification)
        }
    }

    private fun acquireStreamWakeLock() {
        if (streamWakeLock?.isHeld == true) return
        streamWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenNOW:ActiveStream")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseStreamWakeLock() {
        streamWakeLock?.takeIf { it.isHeld }?.release()
        streamWakeLock = null
    }
}

private fun ensureStreamNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager = context.applicationContext.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        STREAM_CHANNEL_ID,
        "Active stream",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Keeps an active OpenNOW stream connected while the screen is off."
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
    }
    notificationManager.createNotificationChannel(channel)
}

private fun buildStreamNotification(context: Context, title: String): Notification {
    val appContext = context.applicationContext
    val openIntent = Intent(appContext, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        appContext,
        1,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, STREAM_CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }
    return builder
        .setSmallIcon(R.drawable.ic_tab_stream)
        .setContentTitle(title)
        .setContentText("Streaming continues while the screen is off")
        .setSubText("OpenNOW")
        .setCategory(Notification.CATEGORY_TRANSPORT)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setContentIntent(pendingIntent)
        .build()
}
