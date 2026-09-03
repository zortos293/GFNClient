package com.opencloudgaming.opennow

import android.view.InputDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalMouseDevicesTest {
    @Test
    fun recognizesAbsoluteAndCapturedRelativeMouseSources() {
        assertTrue(isMouseInputSource(InputDevice.SOURCE_MOUSE))
        assertTrue(isMouseInputSource(InputDevice.SOURCE_MOUSE_RELATIVE))
    }

    @Test
    fun doesNotTreatTouchscreenOrControllerAsAMouse() {
        assertFalse(isMouseInputSource(InputDevice.SOURCE_TOUCHSCREEN))
        assertFalse(isMouseInputSource(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK))
    }

    @Test
    fun recognizesOnlyAlphabeticKeyboardSourcesAsPhysicalTypingDevices() {
        assertTrue(
            isKeyboardInputSource(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC),
        )
        assertFalse(
            isKeyboardInputSource(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC),
        )
        assertFalse(
            isKeyboardInputSource(InputDevice.SOURCE_GAMEPAD, InputDevice.KEYBOARD_TYPE_ALPHABETIC),
        )
    }
}
