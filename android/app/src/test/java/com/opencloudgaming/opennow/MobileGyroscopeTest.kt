package com.opencloudgaming.opennow

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileGyroscopeTest {
    @Test
    fun portraitMapsDeviceYawAndPitchToScreenRelativeAngularVelocity() {
        val horizontal = gyroscopeAimForScreen(
            rotation = Surface.ROTATION_0,
            angularVelocityX = 0f,
            angularVelocityY = -2.5f,
            sensitivity = 1f,
            deadZone = 0f,
            invertHorizontal = false,
            invertVertical = false,
        )
        val vertical = gyroscopeAimForScreen(
            rotation = Surface.ROTATION_0,
            angularVelocityX = -2.5f,
            angularVelocityY = 0f,
            sensitivity = 1f,
            deadZone = 0f,
            invertHorizontal = false,
            invertVertical = false,
        )

        assertEquals(2.5f, horizontal.x, 0.001f)
        assertEquals(0f, horizontal.y, 0.001f)
        assertEquals(0f, vertical.x, 0.001f)
        assertEquals(2.5f, vertical.y, 0.001f)
    }

    @Test
    fun landscapeRotationUsesScreenRelativeAxes() {
        val sample = gyroscopeAimForScreen(
            rotation = Surface.ROTATION_90,
            angularVelocityX = 2.5f,
            angularVelocityY = 0f,
            sensitivity = 1f,
            deadZone = 0f,
            invertHorizontal = false,
            invertVertical = false,
        )

        assertEquals(2.5f, sample.x, 0.001f)
        assertEquals(0f, sample.y, 0.001f)
    }

    @Test
    fun deadZoneAndInversionAreAppliedBeforeSending() {
        val quiet = gyroscopeAimForScreen(
            rotation = Surface.ROTATION_0,
            angularVelocityX = 0.01f,
            angularVelocityY = 0.01f,
            sensitivity = 1f,
            deadZone = 0.05f,
            invertHorizontal = false,
            invertVertical = false,
        )
        val inverted = gyroscopeAimForScreen(
            rotation = Surface.ROTATION_0,
            angularVelocityX = -1f,
            angularVelocityY = -1f,
            sensitivity = 1f,
            deadZone = 0f,
            invertHorizontal = true,
            invertVertical = true,
        )

        assertEquals(0f, quiet.x, 0.001f)
        assertEquals(0f, quiet.y, 0.001f)
        assertEquals(inverted.x, inverted.y, 0.001f)
        assertEquals(true, inverted.x < 0f)
    }

    @Test
    fun angularVelocityIntegratesIntoMouseDeltaAndCapsResumeGaps() {
        val normal = gyroscopeMouseDelta(
            angularVelocity = androidx.compose.ui.geometry.Offset(2f, -1f),
            elapsedSeconds = 0.01f,
        )
        val resumed = gyroscopeMouseDelta(
            angularVelocity = androidx.compose.ui.geometry.Offset(2f, -1f),
            elapsedSeconds = 1f,
        )

        assertEquals(10f, normal.x, 0.001f)
        assertEquals(-5f, normal.y, 0.001f)
        assertEquals(50f, resumed.x, 0.001f)
        assertEquals(-25f, resumed.y, 0.001f)
    }
}
