package com.opencloudgaming.opennow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import kotlin.system.exitProcess

private const val APP_DATA_RESET_LOG_TAG = "OpenNOW.AppDataReset"
private const val APP_DATA_RESET_RELAUNCH_DELAY_MS = 500L
private const val APP_DATA_RESET_RELAUNCH_REQUEST_CODE = 1007

internal fun wipeAppDataAndRelaunch(context: Context): Nothing {
    val appContext = context.applicationContext
    val relaunchIntent = buildAppRelaunchIntent(appContext)
    val relaunchPendingIntent = PendingIntent.getActivity(
        appContext,
        APP_DATA_RESET_RELAUNCH_REQUEST_CODE,
        relaunchIntent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    alarmManager?.set(
        AlarmManager.ELAPSED_REALTIME,
        SystemClock.elapsedRealtime() + APP_DATA_RESET_RELAUNCH_DELAY_MS,
        relaunchPendingIntent,
    )
    clearAppDataDirectories(appContext)
    Process.killProcess(Process.myPid())
    exitProcess(0)
}

private fun buildAppRelaunchIntent(context: Context): Intent =
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(context, MainActivity::class.java)).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }

private fun clearAppDataDirectories(context: Context) {
    val dataDir = context.dataDir
    val children = dataDir.listFiles().orEmpty()
    if (children.isEmpty()) {
        deleteKnownAppDataDirectories(context)
        return
    }
    children.forEach { child ->
        if (child.name == "lib") return@forEach
        deleteAppDataPath(child)
    }
}

private fun deleteKnownAppDataDirectories(context: Context) {
    listOf(
        context.filesDir,
        context.cacheDir,
        context.codeCacheDir,
        context.noBackupFilesDir,
        File(context.dataDir, "shared_prefs"),
        File(context.dataDir, "databases"),
    ).forEach(::deleteAppDataPath)
}

private fun deleteAppDataPath(path: File?) {
    if (path == null || !path.exists()) return
    val deleted = runCatching { path.deleteRecursively() }
        .onFailure { error -> Log.w(APP_DATA_RESET_LOG_TAG, "Failed to delete ${path.name}", error) }
        .getOrDefault(false)
    if (!deleted && path.exists()) {
        Log.w(APP_DATA_RESET_LOG_TAG, "Failed to delete ${path.name}")
    }
}
