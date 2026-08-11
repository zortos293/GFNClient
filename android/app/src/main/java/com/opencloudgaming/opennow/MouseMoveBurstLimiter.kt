package com.opencloudgaming.opennow

/** One mouse movement packet after burst limiting has combined adjacent deltas. */
internal data class MouseMoveBatch(
    val dx: Int,
    val dy: Int,
    val partiallyReliable: Boolean,
)

/**
 * Bounds high-rate physical mouse traffic without delaying the first movement in a burst.
 *
 * The first delta after an idle period is returned immediately. Deltas that arrive before
 * [minimumIntervalMs] has elapsed are accumulated for one trailing packet, preserving total
 * movement while preventing 500 Hz mice from creating 500 SCTP sends and worker tasks per second.
 * The state machine is deliberately independent of Android/WebRTC so scheduling stays testable.
 */
internal class MouseMoveBurstLimiter(
    private val minimumIntervalMs: Long,
) {
    init {
        require(minimumIntervalMs > 0L)
    }

    private var lastSentAtMs: Long? = null
    private var pendingDx = 0L
    private var pendingDy = 0L
    private var pendingPartiallyReliable = true

    val hasPendingMovement: Boolean
        get() = pendingDx != 0L || pendingDy != 0L

    /**
     * Returns a packet for immediate sending, or null when this delta belongs in the trailing
     * packet for the current interval.
     */
    fun offer(dx: Int, dy: Int, partiallyReliable: Boolean, nowMs: Long): MouseMoveBatch? {
        if (dx == 0 && dy == 0) return null
        val lastSent = lastSentAtMs
        if (lastSent == null || nowMs - lastSent >= minimumIntervalMs) {
            addPending(dx, dy, partiallyReliable)
            return takePending(nowMs)
        }
        addPending(dx, dy, partiallyReliable)
        return null
    }

    fun delayUntilFlushMs(nowMs: Long): Long? {
        if (!hasPendingMovement) return null
        val lastSent = lastSentAtMs ?: return 0L
        return (minimumIntervalMs - (nowMs - lastSent)).coerceAtLeast(0L)
    }

    /** Flushes accumulated movement now, preserving all deltas and reliability requirements. */
    fun flush(nowMs: Long): MouseMoveBatch? = takePending(nowMs)

    fun reset() {
        lastSentAtMs = null
        pendingDx = 0L
        pendingDy = 0L
        pendingPartiallyReliable = true
    }

    private fun addPending(dx: Int, dy: Int, partiallyReliable: Boolean) {
        pendingDx += dx.toLong()
        pendingDy += dy.toLong()
        pendingPartiallyReliable = pendingPartiallyReliable && partiallyReliable
    }

    private fun takePending(nowMs: Long): MouseMoveBatch? {
        if (!hasPendingMovement) return null
        val batch = MouseMoveBatch(
            dx = pendingDx.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            dy = pendingDy.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            partiallyReliable = pendingPartiallyReliable,
        )
        pendingDx = 0L
        pendingDy = 0L
        pendingPartiallyReliable = true
        lastSentAtMs = nowMs
        return batch
    }
}
