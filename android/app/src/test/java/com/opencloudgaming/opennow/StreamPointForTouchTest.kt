package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Direct click has no absolute-positioning packet to lean on — the wire format only carries
 * relative motion — so it maps a touch into stream space and sends the delta from a shadow copy of
 * the host's cursor. That makes [streamPointForTouch] the whole feature's correctness surface, and
 * one property in particular has already regressed once in this file's history: a view resize (PiP,
 * rotation, minimise) must change nothing about where a proportionally-equivalent touch lands,
 * because the host's cursor does not move when our window does.
 */
class StreamPointForTouchTest {

    private fun map(
        touchX: Float,
        touchY: Float,
        viewWidth: Int,
        viewHeight: Int,
        streamWidth: Int = 1920,
        streamHeight: Int = 1080,
        stretchToFit: Boolean = false,
        renderingAspectRatio: Float = 0f,
    ) = streamPointForTouch(
        touchX = touchX,
        touchY = touchY,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        streamWidth = streamWidth,
        streamHeight = streamHeight,
        stretchToFit = stretchToFit,
        renderingAspectRatio = renderingAspectRatio,
    )

    @Test
    fun matchingAspectRatioMapsProportionally() {
        // 16:9 view onto a 16:9 stream — no bars, so it is a straight scale.
        val point = map(touchX = 480f, touchY = 270f, viewWidth = 1280, viewHeight = 720)
        assertEquals(720f, point.x, 0.01f)
        assertEquals(405f, point.y, 0.01f)
    }

    @Test
    fun pillarboxedViewDiscountsTheSideBars() {
        // 2:1 view, 16:9 stream: the video is 1280x720 centred in 1440x720, bars 80px each side.
        val centre = map(touchX = 720f, touchY = 360f, viewWidth = 1440, viewHeight = 720)
        assertEquals(960f, centre.x, 0.01f)
        assertEquals(540f, centre.y, 0.01f)

        // The left edge of the *video*, not of the view.
        val videoLeftEdge = map(touchX = 80f, touchY = 360f, viewWidth = 1440, viewHeight = 720)
        assertEquals(0f, videoLeftEdge.x, 0.01f)
    }

    @Test
    fun letterboxedViewDiscountsTheTopAndBottomBars() {
        // 4:3 view, 16:9 stream: video is 960x540 centred in 960x720, bars 90px top and bottom.
        val centre = map(touchX = 480f, touchY = 360f, viewWidth = 960, viewHeight = 720)
        assertEquals(960f, centre.x, 0.01f)
        assertEquals(540f, centre.y, 0.01f)

        val videoTopEdge = map(touchX = 480f, touchY = 90f, viewWidth = 960, viewHeight = 720)
        assertEquals(0f, videoTopEdge.y, 0.01f)
    }

    @Test
    fun touchesOnTheBarsClampIntoTheStream() {
        val onLeftBar = map(touchX = 10f, touchY = 360f, viewWidth = 1440, viewHeight = 720)
        assertEquals(0f, onLeftBar.x, 0.01f)

        val onRightBar = map(touchX = 1430f, touchY = 360f, viewWidth = 1440, viewHeight = 720)
        assertEquals(1920f, onRightBar.x, 0.01f)
    }

    @Test
    fun stretchToFitIgnoresAspectRatioEntirely() {
        // Same 4:3 view as the letterbox case, but stretched: no bars to discount.
        val point = map(touchX = 480f, touchY = 360f, viewWidth = 960, viewHeight = 720, stretchToFit = true)
        assertEquals(960f, point.x, 0.01f)
        assertEquals(540f, point.y, 0.01f)

        val topEdge = map(touchX = 480f, touchY = 0f, viewWidth = 960, viewHeight = 720, stretchToFit = true)
        assertEquals(0f, topEdge.y, 0.01f)
    }

    @Test
    fun renderingAspectRatioOverridesTheResolutionRatio() {
        // An ultrawide 21:9 render inside a 16:9 stream buffer: the bars follow what is actually
        // rendered, not what the resolution string implies.
        val point = map(
            touchX = 640f,
            touchY = 360f,
            viewWidth = 1280,
            viewHeight = 720,
            renderingAspectRatio = 21f / 9f,
        )
        // Video is 1280x548.57 centred in 1280x720, so the view centre sits at the video centre.
        assertEquals(960f, point.x, 0.01f)
        assertEquals(540f, point.y, 0.5f)
    }

    /**
     * The PiP regression, stated as a property. A window resize must not move where an equivalent
     * touch lands — that is what lets the cursor shadow survive PiP untouched.
     */
    @Test
    fun resizingTheViewDoesNotMoveWhereAnEquivalentTouchLands() {
        val fullscreen = map(touchX = 1152f, touchY = 576f, viewWidth = 1280, viewHeight = 720)
        // Same fraction of a PiP-sized window: 90% across, 80% down.
        val pip = map(touchX = 288f, touchY = 144f, viewWidth = 320, viewHeight = 180)

        assertEquals(fullscreen.x, pip.x, 0.01f)
        assertEquals(fullscreen.y, pip.y, 0.01f)
    }

    @Test
    fun degenerateSizesDoNotDivideByZero() {
        assertEquals(StreamPoint(0f, 0f), map(touchX = 10f, touchY = 10f, viewWidth = 0, viewHeight = 720))
        assertEquals(StreamPoint(0f, 0f), map(touchX = 10f, touchY = 10f, viewWidth = 1280, viewHeight = 0))
        assertEquals(
            StreamPoint(0f, 0f),
            map(touchX = 10f, touchY = 10f, viewWidth = 1280, viewHeight = 720, streamWidth = 0),
        )
    }
}
