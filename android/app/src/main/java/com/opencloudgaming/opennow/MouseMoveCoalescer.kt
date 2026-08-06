package com.opencloudgaming.opennow

/**
 * The accumulated mouse delta of one coalescing window.
 *
 * [dx]/[dy] are the summed deltas to encode into a single packet; [partiallyReliable] is the AND
 * of every add that fed this batch, so a window that ever asked for reliability is sent reliably
 * (conservative: reliability is never silently downgraded).
 */
internal data class MouseMoveBatch(
    val dx: Int,
    val dy: Int,
    val partiallyReliable: Boolean,
)

/**
 * Pure state machine for client-side mouse-move coalescing.
 *
 * External mice and controller-mouse emulation can emit 125-500 small deltas per second. Sending
 * each as its own SCTP packet makes the transport carry far more packets than the input needs,
 * which raises the chance of loss and head-of-line stalls on the reliable channel. This class
 * accumulates deltas into a window that the caller flushes once per [NativeStreamClient] window,
 * turning a flood of tiny packets into a single one at the cost of at most one window of latency.
 *
 * It is deliberately free of WebRTC, coroutines and locking: it only tracks integer state, so it
 * can be unit-tested on the JVM. Callers that share it across threads must guard it themselves
 * (NativeStreamClient holds [Any]-based `mouseMoveLock` around every call).
 */
internal class MouseMoveCoalescer {

    private var pendingDx = 0
    private var pendingDy = 0
    private var pendingPartiallyReliable = true

    /**
     * Whether a flush is due — an add happened since the last flush. A window filled only with
     * zero deltas never becomes dirty, so the caller does not schedule an empty flush.
     */
    val needsFlush: Boolean
        get() = pendingDx != 0 || pendingDy != 0

    /**
     * Merges one delta into the current window. A (0, 0) delta is ignored entirely: it cannot
     * offset later deltas, and if it carried `partiallyReliable = false` it would pin the whole
     * next window to reliable without ever dirtying it.
     */
    fun add(dx: Int, dy: Int, partiallyReliable: Boolean) {
        if (dx == 0 && dy == 0) return
        pendingDx += dx
        pendingDy += dy
        pendingPartiallyReliable = pendingPartiallyReliable && partiallyReliable
    }

    /**
     * Returns the accumulated batch and resets the window, or null when there is nothing to send.
     * The next window starts fresh (reliability resets to the default), so a batch that demanded
     * reliability does not leak into the following one.
     */
    fun flush(): MouseMoveBatch? {
        if (pendingDx == 0 && pendingDy == 0) return null
        val batch = MouseMoveBatch(pendingDx, pendingDy, pendingPartiallyReliable)
        pendingDx = 0
        pendingDy = 0
        pendingPartiallyReliable = true
        return batch
    }
}
