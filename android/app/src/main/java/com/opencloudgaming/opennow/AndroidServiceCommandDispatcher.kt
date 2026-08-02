package com.opencloudgaming.opennow

import android.util.Log
import java.util.concurrent.Executors

/** Keeps service and notification Binder calls ordered and off the UI thread. */
internal object AndroidServiceCommandDispatcher {
    private const val TAG = "OpenNOWServiceCommands"
    private val executor = Executors.newSingleThreadExecutor { command ->
        Thread(command, "opennow-service-commands").apply {
            priority = Thread.NORM_PRIORITY
        }
    }

    fun dispatch(label: String, command: () -> Unit) {
        runCatching {
            executor.execute {
                runCatching(command).onFailure { error ->
                    Log.w(TAG, "Service command failed: $label", error)
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to queue service command: $label", error)
        }
    }
}
