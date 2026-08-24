package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerFocusFrameTest {
    @Test
    fun enhancedFrameRequiresFocusedTvOrPhysicalControllerCard() {
        assertTrue(
            shouldShowEnhancedControllerFocus(
                focused = true,
                tvProfile = true,
                controllerActionMode = false,
            ),
        )
        assertTrue(
            shouldShowEnhancedControllerFocus(
                focused = true,
                tvProfile = false,
                controllerActionMode = true,
            ),
        )
        assertFalse(
            shouldShowEnhancedControllerFocus(
                focused = true,
                tvProfile = false,
                controllerActionMode = false,
            ),
        )
        assertFalse(
            shouldShowEnhancedControllerFocus(
                focused = false,
                tvProfile = true,
                controllerActionMode = false,
            ),
        )
    }

    @Test
    fun energyOrbitLoopsAndKeepsStaticFlickerBounded() {
        assertEquals(0f, controllerFocusOrbitPhasePx(progress = 0f, perimeterPx = 240f), 0f)
        assertEquals(120f, controllerFocusOrbitPhasePx(progress = 0.5f, perimeterPx = 240f), 0f)
        assertEquals(0f, controllerFocusOrbitPhasePx(progress = 1f, perimeterPx = 240f), 0f)
        assertEquals(0, controllerFocusStaticStep(0f))
        assertEquals(24, controllerFocusStaticStep(0.5f))
        assertEquals(0, controllerFocusStaticStep(1f))
        (0..100).forEach { step ->
            assertTrue(controllerFocusFlickerAlpha(step / 100f) in 0.72f..1f)
        }
    }

    @Test
    fun interactionFocusFallsBackToOneStaticLineWithoutCinemaMotion() {
        assertTrue(shouldDrawStaticInteractionFocus(visible = true, cinemaEffectEnabled = false))
        assertFalse(shouldDrawStaticInteractionFocus(visible = true, cinemaEffectEnabled = true))
        assertFalse(shouldDrawStaticInteractionFocus(visible = false, cinemaEffectEnabled = false))
    }
}
