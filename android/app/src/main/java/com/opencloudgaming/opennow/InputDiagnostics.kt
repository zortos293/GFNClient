package com.opencloudgaming.opennow

import android.os.SystemClock
import android.util.Log

internal class InputDiagnosticsBuffer(
    private val maxRecentLines: Int,
    private val maxRetainedLines: Int,
    private val elapsedRealtime: () -> Long,
) {
    private val recentLines = ArrayDeque<String>()
    private val retainedLines = linkedMapOf<String, String>()
    private val retainedUpdatedAtMs = mutableMapOf<String, Long>()
    private val retainedCounts = mutableMapOf<String, Long>()

    init {
        require(maxRecentLines > 0)
        require(maxRetainedLines > 0)
    }

    fun add(message: String): String {
        val line = formatLine(elapsedRealtime(), message)
        addRecentLine(line)
        return line
    }

    fun addRetained(key: String, message: String): String {
        val now = elapsedRealtime()
        val line = formatLine(now, message)
        addRecentLine(line)
        retainLine(key, now, line)
        return line
    }

    private fun addRecentLine(line: String) {
        if (recentLines.size >= maxRecentLines) {
            recentLines.removeFirst()
        }
        recentLines.addLast(line)
    }

    fun retain(key: String, message: String): String =
        retainAt(key, elapsedRealtime(), message)

    fun retainCounted(key: String, message: () -> String): String {
        return retainCountedAt(key, elapsedRealtime(), message())
    }

    fun retainResult(
        keyPrefix: String,
        succeeded: Boolean,
        message: () -> String,
    ) {
        val now = elapsedRealtime()
        val detail = message()
        retainCountedAt("$keyPrefix.last", now, "success=$succeeded $detail")
        retainCountedAt("$keyPrefix.${if (succeeded) "success" else "failure"}", now, detail)
    }

    fun retainThrottled(
        key: String,
        minimumIntervalMs: Long,
        message: () -> String,
    ): String? {
        require(minimumIntervalMs >= 0)
        val now = elapsedRealtime()
        val lastUpdate = retainedUpdatedAtMs[key]
        if (lastUpdate != null && now - lastUpdate in 0 until minimumIntervalMs) {
            return null
        }
        return retainAt(key, now, message())
    }

    fun snapshot(): String {
        if (retainedLines.isEmpty() && recentLines.isEmpty()) {
            return "input.diagnostics=empty"
        }
        return buildString {
            if (retainedLines.isNotEmpty()) {
                appendLine("input.state:")
                retainedLines.forEach { (key, line) -> appendLine("$key $line") }
            }
            if (recentLines.isNotEmpty()) {
                appendLine("input.diagnostics:")
                recentLines.forEach { appendLine(it) }
            }
        }.trimEnd()
    }

    private fun retainAt(key: String, now: Long, message: String): String {
        val line = formatLine(now, message)
        retainLine(key, now, line)
        return line
    }

    private fun retainCountedAt(key: String, now: Long, message: String): String {
        val count = (retainedCounts[key] ?: 0L) + 1L
        retainedCounts[key] = count
        return retainAt(key, now, "count=$count $message")
    }

    private fun retainLine(key: String, now: Long, line: String) {
        if (key !in retainedLines && retainedLines.size >= maxRetainedLines) {
            retainedLines.keys.firstOrNull()?.let { oldestKey ->
                retainedLines.remove(oldestKey)
                retainedUpdatedAtMs.remove(oldestKey)
                retainedCounts.remove(oldestKey)
            }
        }
        retainedLines[key] = line
        retainedUpdatedAtMs[key] = now
    }

    private fun formatLine(now: Long, message: String): String = "$now $message"
}

object NativeInputDiagnostics {
    private const val MAX_RECENT_LINES = 240
    private const val MAX_RETAINED_LINES = 48
    private const val TAG = "OpenNOWInput"
    private val buffer = InputDiagnosticsBuffer(
        maxRecentLines = MAX_RECENT_LINES,
        maxRetainedLines = MAX_RETAINED_LINES,
        elapsedRealtime = SystemClock::elapsedRealtime,
    )

    @Synchronized
    fun add(message: String) {
        buffer.add(message)
        Log.d(TAG, message)
    }

    @Synchronized
    fun addRetained(key: String, message: String) {
        buffer.addRetained(key, message)
        Log.d(TAG, message)
    }

    @Synchronized
    fun retain(key: String, message: String) {
        buffer.retain(key, message)
    }

    @Synchronized
    fun retainCounted(key: String, message: () -> String) {
        buffer.retainCounted(key, message)
    }

    @Synchronized
    fun retainThrottled(key: String, minimumIntervalMs: Long, message: () -> String) {
        buffer.retainThrottled(key, minimumIntervalMs, message)
    }

    @Synchronized
    fun retainResult(keyPrefix: String, succeeded: Boolean, message: () -> String) {
        buffer.retainResult(keyPrefix, succeeded, message)
    }

    @Synchronized
    fun retainTouchRoute(key: String, message: () -> String) {
        buffer.retainCounted("touch-route.$key", message)
    }

    @Synchronized
    fun snapshot(): String = buffer.snapshot()
}
