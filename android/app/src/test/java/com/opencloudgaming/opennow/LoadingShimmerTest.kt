package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingShimmerTest {
    @Test
    fun recreatedPlaceholderKeepsTheDeviceWideSweepPhase() {
        assertEquals(0f, shimmerProgressAtUptime(0L), 0f)
        assertEquals(0.5f, shimmerProgressAtUptime(380L), 0f)
        assertEquals(0f, shimmerProgressAtUptime(760L), 0f)
        assertEquals(0.5f, shimmerProgressAtUptime(1_140L), 0f)
    }

    @Test
    fun sweepRepeatsOnlyWhileHighlightBandIsFullyOutsidePlaceholder() {
        val containerWidth = 300f
        val bandWidth = 156f

        val start = shimmerBandStartX(0f, containerWidth, bandWidth)
        val middle = shimmerBandStartX(0.5f, containerWidth, bandWidth)
        val end = shimmerBandStartX(1f, containerWidth, bandWidth)

        assertEquals(-bandWidth, start, 0f)
        assertEquals(containerWidth, end, 0f)
        assertTrue(start < middle)
        assertTrue(middle < end)
        assertEquals(containerWidth / 2f, middle + bandWidth / 2f, 0f)
    }

    @Test
    fun sweepProgressIsClampedWithoutReversingDirection() {
        assertEquals(-100f, shimmerBandStartX(-1f, 240f, 100f), 0f)
        assertEquals(240f, shimmerBandStartX(2f, 240f, 100f), 0f)
    }
}
