package com.opencloudgaming.opennow

enum class StreamInputMode {
    NativeTouch,
    KeyboardMouse,
}

internal enum class StreamInputModePrompt {
    SwitchToKeyboardMouse,
    SwitchToNativeTouch,
}

/**
 * A device already attached when streaming starts is an explicit enough signal to start in
 * keyboard/mouse mode. Later connection changes require confirmation and stay session-local.
 */
internal fun streamInputModeAtStart(
    nativeTouchAvailable: Boolean,
    keyboardMouseConnected: Boolean,
): StreamInputMode = if (nativeTouchAvailable && !keyboardMouseConnected) {
    StreamInputMode.NativeTouch
} else {
    StreamInputMode.KeyboardMouse
}

internal fun streamInputModePromptForConnectionChange(
    currentMode: StreamInputMode,
    keyboardMouseConnected: Boolean,
    nativeTouchProvisionedForSession: Boolean,
): StreamInputModePrompt? = when {
    keyboardMouseConnected && currentMode == StreamInputMode.NativeTouch ->
        StreamInputModePrompt.SwitchToKeyboardMouse
    !keyboardMouseConnected &&
        currentMode == StreamInputMode.KeyboardMouse &&
        nativeTouchProvisionedForSession -> StreamInputModePrompt.SwitchToNativeTouch
    else -> null
}
