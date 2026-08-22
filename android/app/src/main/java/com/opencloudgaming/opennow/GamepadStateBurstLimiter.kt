package com.opencloudgaming.opennow

/**
 * Coalesces high-rate analog snapshots while keeping the leading state change immediate.
 *
 * Gamepad packets contain the complete current state, so intermediate stick positions can be
 * replaced by the newest position. Button and trigger edges bypass this limiter.
 */
internal class GamepadStateBurstLimiter(
    private val minimumIntervalMs: Long,
) {
    init {
        require(minimumIntervalMs > 0L)
    }

    private var lastSentAtMs: Long? = null
    private var pendingControllerId: Int? = null

    fun offer(controllerId: Int, nowMs: Long): Int? {
        val lastSent = lastSentAtMs
        if (lastSent == null || nowMs - lastSent >= minimumIntervalMs) {
            pendingControllerId = null
            lastSentAtMs = nowMs
            return controllerId
        }
        pendingControllerId = controllerId
        return null
    }

    fun delayUntilFlushMs(nowMs: Long): Long? {
        if (pendingControllerId == null) return null
        val lastSent = lastSentAtMs ?: return 0L
        return (minimumIntervalMs - (nowMs - lastSent)).coerceAtLeast(0L)
    }

    fun flush(nowMs: Long): Int? {
        val controllerId = pendingControllerId ?: return null
        pendingControllerId = null
        lastSentAtMs = nowMs
        return controllerId
    }

    fun reset() {
        lastSentAtMs = null
        pendingControllerId = null
    }
}
