package com.opencloudgaming.opennow

import android.content.Context

private const val SESSION_TIMER_STORE_NAME = "opennow_session_timer"
private const val KEY_SESSION_ID = "session_id"
private const val KEY_STARTED_AT_MS = "started_at_ms"

internal fun resolveSessionTimerStartedAtMs(
    sessionId: String,
    persistedSessionId: String?,
    persistedStartedAtMs: Long,
    preferredStartedAtMs: Long?,
    nowMs: Long,
): Long {
    val persistedIsValid =
        persistedSessionId == sessionId && persistedStartedAtMs > 0L && persistedStartedAtMs <= nowMs
    if (persistedIsValid) return persistedStartedAtMs

    return preferredStartedAtMs
        ?.takeIf { it > 0L && it <= nowMs }
        ?: nowMs
}

internal class SessionTimerAnchorStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        SESSION_TIMER_STORE_NAME,
        Context.MODE_PRIVATE,
    )
    private val lock = Any()

    fun startedAtMsFor(
        sessionId: String,
        preferredStartedAtMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): Long = synchronized(lock) {
        val startedAtMs = resolveSessionTimerStartedAtMs(
            sessionId = sessionId,
            persistedSessionId = prefs.getString(KEY_SESSION_ID, null),
            persistedStartedAtMs = prefs.getLong(KEY_STARTED_AT_MS, 0L),
            preferredStartedAtMs = preferredStartedAtMs,
            nowMs = nowMs,
        )
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putLong(KEY_STARTED_AT_MS, startedAtMs)
            .commit()
        startedAtMs
    }

    fun clear(sessionId: String) {
        synchronized(lock) {
            if (prefs.getString(KEY_SESSION_ID, null) == sessionId) {
                prefs.edit()
                    .remove(KEY_SESSION_ID)
                    .remove(KEY_STARTED_AT_MS)
                    .commit()
            }
        }
    }
}
