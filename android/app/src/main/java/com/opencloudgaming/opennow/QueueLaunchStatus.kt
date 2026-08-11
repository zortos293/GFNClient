package com.opencloudgaming.opennow

internal fun queueLaunchStatusText(state: OpenNowUiState): String {
    val session = state.streamSession
    val queuePosition = queueDisplayPosition(state)
    return when {
        queuePosition != null -> "Queue position $queuePosition"
        session?.seatSetupStep == 1 -> "Waiting for a rig"
        state.launchPhase.equals("Connecting stream", ignoreCase = true) -> "Connecting stream"
        state.launchPhase.equals("Resuming session", ignoreCase = true) -> "Resuming session"
        state.launchPhase.equals("Setting up rig", ignoreCase = true) -> "Setting up rig"
        else -> "Starting session"
    }
}

internal fun queueDisplayPosition(state: OpenNowUiState): Int? {
    val session = state.streamSession
    if (session?.seatSetupStep == 5) return null
    return state.queuePosition?.takeIf { it > 0 } ?: queueDisplayPosition(session)
}

internal fun queueDisplayPosition(session: SessionInfo?): Int? {
    if (session?.seatSetupStep == 5) return null
    return session?.queuePosition?.takeIf { it > 0 }
}

internal fun shouldShowQueueLaunchStatus(state: OpenNowUiState): Boolean {
    if (state.streamStatus == "idle") return false
    val sessionStatus = state.streamSession?.status
    return sessionStatus == null || sessionStatus !in setOf(2, 3)
}

internal fun isActivelyQueued(state: OpenNowUiState): Boolean =
    queueDisplayPosition(state) != null ||
        (state.streamStatus == "queue" && state.launchPhase.equals("Queue", ignoreCase = true))

internal class QueueReadyNotificationTracker {
    private var queuedSessionId: String? = null

    fun update(state: OpenNowUiState): Boolean {
        if (isActivelyQueued(state)) {
            state.streamSession?.sessionId?.let { queuedSessionId = it }
            return false
        }

        if (state.streamStatus == "idle") {
            reset()
            return false
        }
        if (state.streamStatus != "connecting") return false

        val currentSessionId = state.streamSession?.sessionId
        val completedObservedQueue = currentSessionId != null && currentSessionId == queuedSessionId
        reset()
        return completedObservedQueue
    }

    private fun reset() {
        queuedSessionId = null
    }
}
