package com.opencloudgaming.opennow

internal class RapidTapTracker(
    private val requiredTapCount: Int = 10,
    private val windowMs: Long = 8_000L,
) {
    private val tapTimes = ArrayDeque<Long>()

    init {
        require(requiredTapCount > 0) { "Tap count must be positive." }
        require(windowMs > 0) { "Tap window must be positive." }
    }

    fun recordTap(nowMs: Long): Boolean {
        while (tapTimes.firstOrNull()?.let { nowMs - it > windowMs } == true) {
            tapTimes.removeFirst()
        }
        tapTimes.addLast(nowMs)
        if (tapTimes.size < requiredTapCount) return false

        tapTimes.clear()
        return true
    }
}
