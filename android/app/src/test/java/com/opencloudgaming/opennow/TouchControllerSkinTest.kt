package com.opencloudgaming.opennow

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    fun everySkinHasItsOwnSilhouette() {
        // The whole point of a skin: two of them may never differ by colour alone.
        val silhouettes = TouchControllerStyle.entries.associateWith { touchSkinForm(it).silhouette }
        assertEquals(
            "$silhouettes",
            TouchControllerStyle.entries.size,
            silhouettes.values.toSet().size,
        )
    }

    @Test
    fun theClassicSkinKeepsTheShapesItAlwaysHad() {
        val form = touchSkinForm(TouchControllerStyle.V1)

        assertEquals(TouchCapShape.Circle, form.capShape)
        assertEquals(TouchDpadShape.Cross, form.dpadShape)
        assertEquals(TouchStickShape.Ring, form.stickShape)
        assertEquals(TouchShoulderShape.Pill, form.shoulderShape)
        // No dome, no bloom, no travel: this is the layout people already have muscle memory for.
        assertEquals(1f, form.pressScale, 0f)
        assertEquals(0f, form.gloss, 0f)
        assertEquals(0.dp, form.glow)
    }

    @Test
    fun onlyABladeDpadGoesWithoutArrowheads() {
        TouchControllerStyle.entries.forEach { style ->
            val form = touchSkinForm(style)
            if (form.dpadArrow == TouchDpadArrow.None) {
                assertEquals("$style", TouchDpadShape.Blades, form.dpadShape)
            }
        }
    }

    @Test
    fun opacityFadesTheShadingASkinAddsOnTopOfItsPalette() {
        val full = touchSkinColors(TouchControllerStyle.Arcade, opacity = 1f, accent = Color.White)
        val faded = touchSkinColors(TouchControllerStyle.Arcade, opacity = 0.25f, accent = Color.White)

        assertTrue(faded.sheen(0.5f).alpha < full.sheen(0.5f).alpha)
    }

    @Test
    fun theDpadIsAlwaysWideEnoughForItsFourArms() {
        // Three arms across plus the gaps the arrowheads sit in.
        assertEquals(62f, touchDpadBoxSize(20.dp).value, 0.001f)
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
