package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamInputModeChoiceTest {
    @Test
    fun connectedKeyboardOrMouseWinsOnlyAtStreamStart() {
        assertEquals(
            StreamInputMode.KeyboardMouse,
            streamInputModeAtStart(nativeTouchAvailable = true, keyboardMouseConnected = true),
        )
        assertEquals(
            StreamInputMode.NativeTouch,
            streamInputModeAtStart(nativeTouchAvailable = true, keyboardMouseConnected = false),
        )
    }

    @Test
    fun hotPlugAsksBeforeLeavingNativeTouch() {
        assertEquals(
            StreamInputModePrompt.SwitchToKeyboardMouse,
            streamInputModePromptForConnectionChange(
                currentMode = StreamInputMode.NativeTouch,
                keyboardMouseConnected = true,
                nativeTouchProvisionedForSession = true,
            ),
        )
    }

    @Test
    fun disconnectAsksBeforeReturningToProvisionedNativeTouch() {
        assertEquals(
            StreamInputModePrompt.SwitchToNativeTouch,
            streamInputModePromptForConnectionChange(
                currentMode = StreamInputMode.KeyboardMouse,
                keyboardMouseConnected = false,
                nativeTouchProvisionedForSession = true,
            ),
        )
        assertNull(
            streamInputModePromptForConnectionChange(
                currentMode = StreamInputMode.KeyboardMouse,
                keyboardMouseConnected = false,
                nativeTouchProvisionedForSession = false,
            ),
        )
    }

    @Test
    fun unchangedModeNeedsNoPrompt() {
        assertNull(
            streamInputModePromptForConnectionChange(
                currentMode = StreamInputMode.NativeTouch,
                keyboardMouseConnected = false,
                nativeTouchProvisionedForSession = true,
            ),
        )
        assertNull(
            streamInputModePromptForConnectionChange(
                currentMode = StreamInputMode.KeyboardMouse,
                keyboardMouseConnected = true,
                nativeTouchProvisionedForSession = true,
            ),
        )
    }
}
