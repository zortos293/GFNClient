package com.opencloudgaming.opennow

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchControllerSkinTest {
    @Test
    fun everySkinDistinguishesPressedFromResting() {
        TouchControllerStyle.entries.forEach { style ->
            val skin = touchSkinColors(style, opacity = 1f, accent = Color(0xff42c9ff))
            assertNotEquals("$style fill", skin.fill, skin.pressedFill)
        }
    }

    @Test
    fun opacityScalesEverySkinDown() {
        TouchControllerStyle.entries.forEach { style ->
            val full = touchSkinColors(style, opacity = 1f, accent = Color.White)
            val faded = touchSkinColors(style, opacity = 0.3f, accent = Color.White)
            assertTrue("$style border", faded.border.alpha <= full.border.alpha)
            assertTrue("$style glyph", faded.glyph.alpha <= full.glyph.alpha)
        }
    }

    @Test
    fun accentReachesTheSkinsThatUseOne() {
        val red = Color(0xffff0000)
        val neon = touchSkinColors(TouchControllerStyle.Neon, opacity = 1f, accent = red)
        assertEquals(red.red, neon.border.red, 0.001f)
        assertEquals(0f, neon.border.green, 0.001f)
    }

    @Test
    fun cyclingVisitsEverySkinAndReturnsToTheStart() {
        var style = TouchControllerStyle.V1
        val seen = mutableListOf(style)
        repeat(TouchControllerStyle.entries.size - 1) {
            style = nextTouchControllerStyle(style)
            seen += style
        }
        assertEquals(TouchControllerStyle.entries.toSet(), seen.toSet())
        assertEquals(TouchControllerStyle.V1, nextTouchControllerStyle(style))
    }

    @Test
    fun tintPresetsRoundTripThroughTheirIds() {
        TOUCH_SKIN_TINTS.forEach { option ->
            assertEquals(option.id, touchSkinTintId(option.rgb))
            assertEquals(option.rgb, touchSkinTintForId(option.id))
        }
    }

    @Test
    fun unknownTintFallsBackToTheSkinDefault() {
        assertEquals(TOUCH_SKIN_TINT_DEFAULT_ID, touchSkinTintId(ControllerThemeRgb(1, 2, 3)))
        assertNull(touchSkinTintForId("no-such-tint"))
    }

    @Test
    fun unsetTintUsesTheSkinsOwnAccent() {
        val settings = AndroidTouchSettings(touchControllerStyle = TouchControllerStyle.Retro, touchSkinTint = null)
        assertEquals(defaultTouchSkinAccent(TouchControllerStyle.Retro), touchSkinAccent(settings))
    }

    @Test
    fun tintCyclingVisitsEveryPresetAndReturnsToDefault() {
        var tint: ControllerThemeRgb? = null
        val seen = mutableSetOf<ControllerThemeRgb?>()
        repeat(TOUCH_SKIN_TINTS.size) {
            seen += tint
            tint = nextTouchSkinTint(tint)
        }
        assertEquals(TOUCH_SKIN_TINTS.map { it.rgb }.toSet(), seen)
        assertNull(tint)
    }
}
