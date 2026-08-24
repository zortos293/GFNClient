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

private const val GYROSCOPE_DEAD_ZONE_RANGE_RAD_PER_SECOND = 2.5f
private const val GYROSCOPE_MOUSE_PIXELS_PER_RADIAN = 500f
private const val GYROSCOPE_MAX_SAMPLE_INTERVAL_SECONDS = 0.05f

internal fun hasMobileGyroscope(context: Context): Boolean =
    (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
        ?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

/**
 * Maps Android's device-relative angular velocity into screen-relative mouse-look velocity.
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
    var x = screenHorizontal
    var y = screenVertical
    if (invertHorizontal) x = -x
    if (invertVertical) y = -y

    val magnitude = sqrt(x * x + y * y)
    val threshold = deadZone.coerceIn(0f, 0.2f) * GYROSCOPE_DEAD_ZONE_RANGE_RAD_PER_SECOND
    if (magnitude <= threshold || magnitude == 0f) return Offset.Zero
    val adjustedMagnitude = magnitude - threshold
    val scale = adjustedMagnitude / magnitude * sensitivity.coerceIn(0.25f, 3f)
    return Offset(x * scale, y * scale)
}

internal fun gyroscopeMouseDelta(angularVelocity: Offset, elapsedSeconds: Float): Offset {
    if (
        !angularVelocity.x.isFinite() ||
        !angularVelocity.y.isFinite() ||
        !elapsedSeconds.isFinite() ||
        elapsedSeconds <= 0f
    ) {
        return Offset.Zero
    }
    val boundedElapsed = elapsedSeconds.coerceAtMost(GYROSCOPE_MAX_SAMPLE_INTERVAL_SECONDS)
    return angularVelocity * (boundedElapsed * GYROSCOPE_MOUSE_PIXELS_PER_RADIAN)
}

/**
 * Registers only while motion aiming can reach the game. Angular velocity is integrated into
 * ordered relative mouse deltas, so camera motion stops naturally with the sensor instead of
 * behaving like a held right stick.
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
            client.endGyroscopeMouseAim(android.os.SystemClock.uptimeMillis())
            return@DisposableEffect onDispose {
                client.endGyroscopeMouseAim(android.os.SystemClock.uptimeMillis())
            }
        }

        var filtered = Offset.Zero
        var previousTimestampNs = 0L
        val smoothing = settings.gyroscopeSmoothing.coerceIn(0f, 0.9f)
        client.beginGyroscopeMouseAim()
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
                val previous = previousTimestampNs
                previousTimestampNs = event.timestamp
                if (previous <= 0L || event.timestamp <= previous) return
                val delta = gyroscopeMouseDelta(
                    angularVelocity = filtered,
                    elapsedSeconds = (event.timestamp - previous) / 1_000_000_000f,
                )
                client.sendGyroscopeMouseMove(
                    dx = delta.x,
                    dy = delta.y,
                    eventTimeMs = event.timestamp / 1_000_000L,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val registered = sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (registered) {
            NativeInputDiagnostics.add("Mobile gyroscope mouse aiming active")
        } else {
            client.endGyroscopeMouseAim(android.os.SystemClock.uptimeMillis())
        }
        onDispose {
            sensorManager.unregisterListener(listener)
            client.endGyroscopeMouseAim(android.os.SystemClock.uptimeMillis())
        }
    }
}
