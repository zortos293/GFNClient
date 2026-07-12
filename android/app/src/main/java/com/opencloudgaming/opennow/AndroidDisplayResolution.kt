package com.opencloudgaming.opennow

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/**
 * Returns the real display-mode geometry in the landscape orientation used for streaming.
 * This is intentionally separate from the requested logical stream resolution.
 */
internal fun Context.physicalStreamDisplayResolution(): Pair<Int, Int>? {
    val display = getSystemService(DisplayManager::class.java)
        ?.getDisplay(Display.DEFAULT_DISPLAY)
        ?: return null
    val mode = display.mode
    val width = mode.physicalWidth
    val height = mode.physicalHeight
    if (width <= 0 || height <= 0) return null
    return maxOf(width, height) to minOf(width, height)
}
