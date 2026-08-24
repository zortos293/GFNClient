package com.opencloudgaming.opennow

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import kotlin.math.sqrt

private const val GYROSCOPE_FULL_STICK_RAD_PER_SECOND = 2.5f

internal fun hasMobileGyroscope(context: Context): Boolean =
    (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
        ?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

/**
 * Maps Android's device-relative angular velocity into screen-relative right-stick motion.
 *
 * The display rotation matters on phones because the sensor coordinate system never rotates with
 * the UI. Keeping this pure makes all four rotations testable without a physical sensor.
 */
internal fun gyroscopeAimForScreen(
    rotation: Int,
    angularVelocityX: Float,
    angularVelocityY: Float,
    sensitivity: Float,
    deadZone: Float,
    invertHorizontal: Boolean,
    invertVertical: Boolean,
): Offset {
    if (!angularVelocityX.isFinite() || !angularVelocityY.isFinite()) return Offset.Zero
    val (screenHorizontal, screenVertical) = when (rotation) {
        Surface.ROTATION_90 -> angularVelocityX to -angularVelocityY
        Surface.ROTATION_180 -> angularVelocityY to angularVelocityX
        Surface.ROTATION_270 -> -angularVelocityX to angularVelocityY
        else -> -angularVelocityY to -angularVelocityX
    }
    val gain = sensitivity.coerceIn(0.25f, 3f) / GYROSCOPE_FULL_STICK_RAD_PER_SECOND
    var x = (screenHorizontal * gain).coerceIn(-1f, 1f)
    var y = (screenVertical * gain).coerceIn(-1f, 1f)
    if (invertHorizontal) x = -x
    if (invertVertical) y = -y

    val magnitude = sqrt(x * x + y * y).coerceIn(0f, 1f)
    val threshold = deadZone.coerceIn(0f, 0.2f)
    if (magnitude <= threshold || magnitude == 0f) return Offset.Zero
    val adjustedMagnitude = (magnitude - threshold) / (1f - threshold)
    val scale = adjustedMagnitude / magnitude
    return Offset(x * scale, y * scale)
}

/**
 * Registers only while motion aiming can reach the game. Disposal always sends a centred sample,
 * so opening a menu, disabling gyro, rotating out of the stream, or leaving the session cannot
 * strand the remote camera in motion.
 */
@Composable
internal fun MobileGyroscopeAim(
    client: NativeStreamClient,
    settings: AndroidTouchSettings,
    active: Boolean,
) {
    val view = LocalView.current
    val sensorManager = remember(view.context) {
        view.context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val sensor = remember(sensorManager) { sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    val currentRotation = rememberUpdatedState(view.display?.rotation ?: Surface.ROTATION_0)

    DisposableEffect(
        client,
        sensorManager,
        sensor,
        active,
        settings.gyroscopeEnabled,
        settings.gyroscopeSensitivity,
        settings.gyroscopeDeadZone,
        settings.gyroscopeSmoothing,
        settings.gyroscopeInvertHorizontal,
        settings.gyroscopeInvertVertical,
    ) {
        if (!active || !settings.gyroscopeEnabled || sensorManager == null || sensor == null) {
            client.setGyroscopeRightStick(0f, 0f)
            return@DisposableEffect onDispose { client.setGyroscopeRightStick(0f, 0f) }
        }

        var filtered = Offset.Zero
        val smoothing = settings.gyroscopeSmoothing.coerceIn(0f, 0.9f)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 2) return
                val sample = gyroscopeAimForScreen(
                    rotation = currentRotation.value,
                    angularVelocityX = event.values[0],
                    angularVelocityY = event.values[1],
                    sensitivity = settings.gyroscopeSensitivity,
                    deadZone = settings.gyroscopeDeadZone,
                    invertHorizontal = settings.gyroscopeInvertHorizontal,
                    invertVertical = settings.gyroscopeInvertVertical,
                )
                filtered = Offset(
                    x = filtered.x * smoothing + sample.x * (1f - smoothing),
                    y = filtered.y * smoothing + sample.y * (1f - smoothing),
                )
                client.setGyroscopeRightStick(filtered.x, filtered.y)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (registered) {
            NativeInputDiagnostics.add("Mobile gyroscope aiming active")
        } else {
            client.setGyroscopeRightStick(0f, 0f)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
            client.setGyroscopeRightStick(0f, 0f)
        }
    }
}
