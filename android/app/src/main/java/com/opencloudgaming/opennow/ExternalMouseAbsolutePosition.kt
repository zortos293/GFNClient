package com.opencloudgaming.opennow

import kotlin.math.roundToInt

internal data class AbsoluteMousePosition(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * Tracks a captured physical mouse inside the remote stream extent.
 *
 * Desktop GFN sends absolute type-5 packets while the host cursor is visible. Android pointer
 * capture only supplies relative deltas, so retain the equivalent absolute position locally.
 */
internal class ExternalMouseAbsolutePosition {
    private var x = 0f
    private var y = 0f
    private var initialized = false

    fun moveBy(dx: Int, dy: Int, width: Int, height: Int): AbsoluteMousePosition {
        val safeWidth = width.coerceIn(1, 65535)
        val safeHeight = height.coerceIn(1, 65535)
        if (!initialized) {
            x = safeWidth / 2f
            y = safeHeight / 2f
            initialized = true
        }
        x = (x + dx).coerceIn(0f, safeWidth.toFloat())
        y = (y + dy).coerceIn(0f, safeHeight.toFloat())
        return AbsoluteMousePosition(
            x = x.roundToInt(),
            y = y.roundToInt(),
            width = safeWidth,
            height = safeHeight,
        )
    }

    fun reset() {
        x = 0f
        y = 0f
        initialized = false
    }
}
