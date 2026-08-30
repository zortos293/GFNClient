package com.opencloudgaming.opennow

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

internal fun isMouseInputSource(sources: Int): Boolean =
    (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
        (sources and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE

internal fun isPhysicalMouseDevice(device: InputDevice?): Boolean =
    device != null && !device.isVirtual && isMouseInputSource(device.sources)

internal fun hasConnectedPhysicalMouse(): Boolean = runCatching {
    InputDevice.getDeviceIds().any { deviceId ->
        isPhysicalMouseDevice(InputDevice.getDevice(deviceId))
    }
}.getOrDefault(false)

@Composable
internal fun rememberPhysicalMouseConnected(enabled: Boolean): Boolean {
    val context = LocalContext.current.applicationContext
    var connected by remember(enabled) {
        mutableStateOf(enabled && hasConnectedPhysicalMouse())
    }
    DisposableEffect(context, enabled) {
        fun refresh() {
            connected = enabled && hasConnectedPhysicalMouse()
        }
        refresh()
        if (!enabled) {
            onDispose {}
        } else {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
            val listener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = refresh()
                override fun onInputDeviceRemoved(deviceId: Int) = refresh()
                override fun onInputDeviceChanged(deviceId: Int) = refresh()
            }
            inputManager?.registerInputDeviceListener(listener, null)
            onDispose {
                inputManager?.unregisterInputDeviceListener(listener)
            }
        }
    }
    return connected
}
