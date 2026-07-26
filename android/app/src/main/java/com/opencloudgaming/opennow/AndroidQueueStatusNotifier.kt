package com.opencloudgaming.opennow

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

internal const val QUEUE_CHANNEL_ID = "opennow_queue_status"
internal const val QUEUE_NOTIFICATION_ID = 4210
private const val QUEUE_ALERT_CHANNEL_ID = "opennow_queue_ready"
private const val QUEUE_ALERT_NOTIFICATION_ID = 4212

private const val QUEUE_SERVICE_ACTION_UPDATE = "com.opencloudgaming.opennow.queue.UPDATE"
private const val QUEUE_SERVICE_ACTION_STOP = "com.opencloudgaming.opennow.queue.STOP"
private const val QUEUE_SERVICE_EXTRA_TITLE = "title"
private const val QUEUE_SERVICE_EXTRA_TEXT = "text"
private const val QUEUE_SERVICE_TAG = "OpenNOWQueueService"
private val QUEUE_NOTIFICATION_SMALL_ICON = R.drawable.ic_tab_stream

/** Returns true if the queue wait is over and the game is now launching/loading. */
private fun isQueueComplete(state: OpenNowUiState): Boolean {
    val queuePosition = queueDisplayPosition(state)
    if (queuePosition != null) return false // Still in queue
    val phase = state.launchPhase
    if (phase.isBlank()) return false
    return phase.equals("Connecting stream", ignoreCase = true) ||
        phase.equals("Setting up rig", ignoreCase = true) ||
        phase.equals("Resuming session", ignoreCase = true) ||
        phase.contains("Starting", ignoreCase = true)
}

class AndroidQueueStatusNotifier(private val context: Context) {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private var serviceStartRequested = false
    private var queueReadyAlertSent = false

    fun update(state: OpenNowUiState) {
        if (!shouldShowQueueLaunchStatus(state)) {
            cancel()
            return
        }

        // Reset alert tracker when user is actively queuing so it fires again next time queue completes.
        if (queueDisplayPosition(state) != null) {
            queueReadyAlertSent = false
        }

        // Send a one-shot high-priority heads-up alert when queue finishes and game is loading.
        if (!queueReadyAlertSent && isQueueComplete(state)) {
            queueReadyAlertSent = true
            if (canPostNotifications()) {
                runCatching {
                    ensureQueueAlertChannel(appContext)
                    notificationManager.notify(
                        QUEUE_ALERT_NOTIFICATION_ID,
                        buildQueueReadyNotification(appContext, state.streamGame?.title ?: "OpenNOW"),
                    )
                }.onFailure { error ->
                    Log.w(QUEUE_SERVICE_TAG, "Unable to post queue-ready alert notification", error)
                }
            }
        }

        ensureChannel()
        val intent = Intent(appContext, AndroidQueueStatusService::class.java).apply {
            action = QUEUE_SERVICE_ACTION_UPDATE
            putExtra(QUEUE_SERVICE_EXTRA_TITLE, state.streamGame?.title ?: "OpenNOW")
            putExtra(QUEUE_SERVICE_EXTRA_TEXT, queueLaunchStatusText(state))
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
            serviceStartRequested = true
        }.onFailure { error ->
            Log.w(QUEUE_SERVICE_TAG, "Unable to start queue foreground service", error)
            if (canPostNotifications()) {
                runCatching {
                    notificationManager.notify(
                        QUEUE_NOTIFICATION_ID,
                        buildQueueNotification(appContext, state.streamGame?.title ?: "OpenNOW", queueLaunchStatusText(state)),
                    )
                }.onFailure { notifyError ->
                    Log.w(QUEUE_SERVICE_TAG, "Unable to post fallback queue notification", notifyError)
                }
            }
        }
    }

    fun cancel() {
        val intent = Intent(appContext, AndroidQueueStatusService::class.java).apply {
            action = QUEUE_SERVICE_ACTION_STOP
        }
        runCatching {
            if (serviceStartRequested) {
                appContext.startService(intent)
            } else {
                appContext.stopService(intent)
            }
        }.onFailure { error ->
            Log.w(QUEUE_SERVICE_TAG, "Unable to stop queue foreground service", error)
        }
        serviceStartRequested = false
        queueReadyAlertSent = false
        notificationManager.cancel(QUEUE_NOTIFICATION_ID)
        notificationManager.cancel(QUEUE_ALERT_NOTIFICATION_ID)
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        ensureQueueNotificationChannel(appContext)
    }

    private fun ensureAlertChannel() {
        ensureQueueAlertChannel(appContext)
    }
}

class AndroidQueueStatusService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching {
            when (intent?.action) {
                QUEUE_SERVICE_ACTION_STOP -> {
                    startQueueForeground("OpenNOW", "Queue status")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
                QUEUE_SERVICE_ACTION_UPDATE, null -> {
                    val title = intent?.getStringExtra(QUEUE_SERVICE_EXTRA_TITLE) ?: "OpenNOW"
                    val text = intent?.getStringExtra(QUEUE_SERVICE_EXTRA_TEXT) ?: "Queue status"
                    startQueueForeground(title, text)
                }
                else -> {
                    startQueueForeground("OpenNOW", "Queue status")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }.onFailure { error ->
            Log.e(QUEUE_SERVICE_TAG, "Queue foreground service failed to start", error)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(QUEUE_SERVICE_TAG, "Queue foreground service timed out startId=$startId type=$fgsType; stopping")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startQueueForeground(title: String, text: String) {
        ensureQueueNotificationChannel(this)
        val notification = buildQueueNotification(this, title, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(QUEUE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(QUEUE_NOTIFICATION_ID, notification)
        }
    }
}

private fun ensureQueueNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager = context.applicationContext.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        QUEUE_CHANNEL_ID,
        "Queue status",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Shows OpenNOW queue and session startup progress."
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
    }
    notificationManager.createNotificationChannel(channel)
}

private fun buildQueueNotification(context: Context, title: String, text: String): Notification {
    val appContext = context.applicationContext
    val openIntent = Intent(appContext, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, QUEUE_CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }
    return builder
        .setSmallIcon(QUEUE_NOTIFICATION_SMALL_ICON)
        .setContentTitle(title)
        .setContentText(text)
        .setSubText("OpenNOW")
        .setCategory(Notification.CATEGORY_PROGRESS)
        .setProgress(0, 0, true)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setShowWhen(false)
        .setContentIntent(pendingIntent)
        .build()
}

private fun ensureQueueAlertChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val notificationManager = context.applicationContext.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        QUEUE_ALERT_CHANNEL_ID,
        "Queue ready alert",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Alerts when a GFN queue finishes and the game is about to launch."
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        setShowBadge(true)
        enableVibration(true)
    }
    notificationManager.createNotificationChannel(channel)
}

private fun buildQueueReadyNotification(context: Context, gameTitle: String): Notification {
    val appContext = context.applicationContext
    val openIntent = Intent(appContext, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        appContext,
        2,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(appContext, QUEUE_ALERT_CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(appContext)
    }
    return builder
        .setSmallIcon(QUEUE_NOTIFICATION_SMALL_ICON)
        .setContentTitle("$gameTitle is ready to play!")
        .setContentText("Your GFN queue is done. Tap to return to the app.")
        .setSubText("OpenNOW")
        .setCategory(Notification.CATEGORY_ALARM)
        .setVisibility(Notification.VISIBILITY_PUBLIC)
        .setOngoing(false)
        .setAutoCancel(true)
        .setShowWhen(true)
        .setContentIntent(pendingIntent)
        .build()
}
