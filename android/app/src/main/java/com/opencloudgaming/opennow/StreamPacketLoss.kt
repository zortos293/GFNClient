package com.opencloudgaming.opennow

import java.util.ArrayDeque

internal data class StreamPacketDelta(
    val lost: Long,
    val received: Long,
)

/**
 * Returns a usable delta only while WebRTC is reporting the same monotonically increasing packet
 * counters. Counter resets happen during SSRC/transport changes and must not be presented as loss.
 */
internal fun streamPacketDelta(
    currentLost: Long,
    currentReceived: Long,
    previousLost: Long,
    previousReceived: Long,
): StreamPacketDelta? {
    if (currentLost < previousLost || currentReceived < previousReceived) return null
    return StreamPacketDelta(
        lost = currentLost - previousLost,
        received = currentReceived - previousReceived,
    )
}

/**
 * Smooths the one-second WebRTC packet counters into a short rolling measurement. A sparse delta
 * can otherwise make the overlay jump from 0% to 50% for one sample; three samples are enough to
 * avoid that misleading flash while five seconds remains responsive to a real network problem.
 */
internal class StreamPacketLossWindow(
    private val maximumSamples: Int = 5,
    private val minimumSamples: Int = 3,
) {
    private val samples = ArrayDeque<StreamPacketDelta>()

    init {
        require(maximumSamples > 0)
        require(minimumSamples in 1..maximumSamples)
    }

    fun add(delta: StreamPacketDelta): Double? {
        samples.addLast(delta)
        while (samples.size > maximumSamples) samples.removeFirst()
        if (samples.size < minimumSamples) return null

        val lost = samples.sumOf(StreamPacketDelta::lost)
        val received = samples.sumOf(StreamPacketDelta::received)
        val total = lost + received
        return if (total > 0L) {
            (lost.toDouble() / total.toDouble() * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
    }

    fun reset() {
        samples.clear()
    }
}
