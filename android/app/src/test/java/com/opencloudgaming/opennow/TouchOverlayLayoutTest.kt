package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchOverlayLayoutTest {
    @Test
    fun keepsLandscapeTopControlsBelowPhoneTopInformationBand() {
        val clearance = landscapeTouchTopControlClearanceDp(viewportHeightDp = 390f, controlScale = 1f)

        assertTrue(clearance >= 40f)
    }

    @Test
    fun scalesLandscapeTopClearanceForLargerControlsWithoutRunningAway() {
        val normal = landscapeTouchTopControlClearanceDp(viewportHeightDp = 430f, controlScale = 1f)
        val large = landscapeTouchTopControlClearanceDp(viewportHeightDp = 800f, controlScale = 1.5f)

        assertTrue(large > normal)
        assertEquals(76f, large, 0.001f)
    }

    @Test
    fun keepsTinyLandscapeScreensUsable() {
        val clearance = landscapeTouchTopControlClearanceDp(viewportHeightDp = 300f, controlScale = 0.6f)

        assertEquals(30f, clearance, 0.001f)
    }

    @Test
    fun touchJoystickDeadZoneKeepsCenterStableAndPreservesFullRange() {
        assertEquals(0f, applyTouchJoystickDeadZone(0.05f, 0.08f), 0.0001f)
        assertEquals(0f, applyTouchJoystickDeadZone(-0.05f, 0.08f), 0.0001f)
        assertEquals(1f, applyTouchJoystickDeadZone(1f, 0.08f), 0.0001f)
        assertEquals(-1f, applyTouchJoystickDeadZone(-1f, 0.08f), 0.0001f)
    }

    @Test
    fun touchJoystickDeadZoneRescalesInputBeyondCenter() {
        assertEquals(0.5f, applyTouchJoystickDeadZone(0.54f, 0.08f), 0.0001f)
        assertEquals(-0.5f, applyTouchJoystickDeadZone(-0.54f, 0.08f), 0.0001f)
    }

    @Test
    fun touchAimZoneMapsFingerTravelToRightStickRange() {
        val halfTravel = touchStickValue(deltaX = 36f, deltaY = -36f, maxTravel = 72f, deadZone = 0f)
        val beyondZone = touchStickValue(deltaX = 144f, deltaY = 0f, maxTravel = 72f, deadZone = 0f)

        assertEquals(0.5f, halfTravel.x, 0.0001f)
        assertEquals(-0.5f, halfTravel.y, 0.0001f)
        assertEquals(1f, beyondZone.x, 0.0001f)
        assertEquals(0f, beyondZone.y, 0.0001f)
    }

    @Test
    fun touchAimZoneUsesTheConfiguredDeadZoneAndRejectsInvalidGeometry() {
        val insideDeadZone = touchStickValue(deltaX = 4f, deltaY = 0f, maxTravel = 72f, deadZone = 0.08f)
        val invalid = touchStickValue(deltaX = 20f, deltaY = 0f, maxTravel = 0f, deadZone = 0f)

        assertEquals(0f, insideDeadZone.x, 0.0001f)
        assertEquals(0f, insideDeadZone.y, 0.0001f)
        assertEquals(0f, invalid.x, 0.0001f)
        assertEquals(0f, invalid.y, 0.0001f)
    }

    @Test
    fun builtInControlVisibilityIsIndependent() {
        val customized = AndroidTouchSettings()
            .withControlVisible(TouchControlGroup.Dpad, false)
            .withControlVisible(TouchControlGroup.RightStick, false)

        assertTrue(customized.isControlVisible(TouchControlGroup.FaceButtons))
        assertEquals(false, customized.isControlVisible(TouchControlGroup.Dpad))
        assertEquals(false, customized.isControlVisible(TouchControlGroup.RightStick))
    }

    @Test
    fun programmableSlotsAreFixedWidthAndCycleThroughOff() {
        val customized = AndroidTouchSettings()
            .withExtraButtonAction(3, TouchExtraButtonAction.RightTrigger)

        assertEquals(TouchExtraButtonAction.RightTrigger, customized.extraButtonAction(3))
        assertEquals(TouchExtraButtonAction.None, nextTouchExtraButtonAction(TouchExtraButtonAction.Select))
        assertEquals(
            TouchExtraButtonAction.RightTrigger,
            customized.withExtraButtonAction(9, TouchExtraButtonAction.A).extraButtonAction(3),
        )
    }
}
