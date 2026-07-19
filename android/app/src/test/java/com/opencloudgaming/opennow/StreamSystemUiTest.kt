package com.opencloudgaming.opennow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSystemUiTest {
    @Test
    fun leavesTransientThreeButtonNavigationVisibleLongEnoughToUse() {
        assertFalse(
            shouldPeriodicallyEnforceStreamSystemUi(
                streamActive = true,
                navigationBarsVisible = true,
            ),
        )
    }

    @Test
    fun keepsFullscreenEnforcementForHiddenNavigationBars() {
        assertTrue(
            shouldPeriodicallyEnforceStreamSystemUi(
                streamActive = true,
                navigationBarsVisible = false,
            ),
        )
        assertFalse(
            shouldPeriodicallyEnforceStreamSystemUi(
                streamActive = false,
                navigationBarsVisible = false,
            ),
        )
    }
}
