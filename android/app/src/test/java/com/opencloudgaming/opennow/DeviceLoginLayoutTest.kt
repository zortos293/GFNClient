package com.opencloudgaming.opennow

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLoginLayoutTest {
    @Test
    fun usesSideBySideLayoutWhenHandheldIsLandscape() {
        assertTrue(
            shouldUseSideBySideDeviceLoginLayout(
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                preferLandscapeLayout = false,
                availableWidthDp = 640,
            ),
        )
    }

    @Test
    fun keepsPortraitDeviceLoginStacked() {
        assertFalse(
            shouldUseSideBySideDeviceLoginLayout(
                orientation = Configuration.ORIENTATION_PORTRAIT,
                preferLandscapeLayout = false,
                availableWidthDp = 640,
            ),
        )
    }

    @Test
    fun keepsCrampedLandscapeDeviceLoginStacked() {
        assertFalse(
            shouldUseSideBySideDeviceLoginLayout(
                orientation = Configuration.ORIENTATION_LANDSCAPE,
                preferLandscapeLayout = false,
                availableWidthDp = 480,
            ),
        )
    }

    @Test
    fun honorsExplicitLandscapePreference() {
        assertTrue(
            shouldUseSideBySideDeviceLoginLayout(
                orientation = Configuration.ORIENTATION_PORTRAIT,
                preferLandscapeLayout = true,
                availableWidthDp = 320,
            ),
        )
    }
}
