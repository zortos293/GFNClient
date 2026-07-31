package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTvUiBehaviorTest {
    @Test
    fun gameDetailsPreferCleanShortDescription() {
        val game = GameInfo(
            id = "fortnite",
            title = "Fortnite",
            description = "Clean short description",
            longDescription = "Provider long description",
        )

        assertEquals("Clean short description", gameDescriptionForDetails(game))
    }

    @Test
    fun restoresTvNavigationFocusOnlyWhenLeavingStream() {
        assertTrue(
            shouldRestoreTvNavigationFocus(
                previouslyInStream = true,
                currentlyInStream = false,
                tvProfile = true,
            ),
        )
        assertFalse(
            shouldRestoreTvNavigationFocus(
                previouslyInStream = true,
                currentlyInStream = false,
                tvProfile = false,
            ),
        )
        assertFalse(
            shouldRestoreTvNavigationFocus(
                previouslyInStream = false,
                currentlyInStream = false,
                tvProfile = true,
            ),
        )
    }

    @Test
    fun catalogWallpaperIsOptInOnBothTvAndMobile() {
        val defaults = AppSettings()

        assertFalse(shouldShowCatalogWallpaper(defaults))
        assertTrue(shouldShowCatalogWallpaper(defaults.copy(nerdCatalogBackground = true)))
    }

    @Test
    fun mobileCatalogCardsUseFullQualityGameBoxArtWithoutTitleOverlay() {
        val gameBoxArt = "https://img.nvidiagrid.net/apps/123/ZZ/GAME_BOX_ART_01_example.jpg"
        val game = GameInfo(
            id = "game",
            title = "Game",
            imageUrl = gameBoxArt,
            tvCardImageUrl = gameBoxArt,
        )

        assertEquals(gameBoxArt, catalogCardImageUrl(game, tvProfile = false))
        assertFalse(shouldOverlayCatalogCardTitle(tvProfile = false))
        assertTrue(shouldShowCatalogCardActions(tvProfile = false, controllerActionMode = false))
    }

    @Test
    fun mobileCatalogCardsRejectStaleTvBannerCacheEntries() {
        val game = GameInfo(
            id = "game",
            title = "Game",
            imageUrl = "https://img.nvidiagrid.net/apps/123/ZZ/TV_BANNER_01_example.jpg",
        )

        assertNull(catalogCardImageUrl(game, tvProfile = false))
    }

    @Test
    fun tvCatalogCardsKeepLowBandwidthArtworkAndTitleOverlay() {
        val game = GameInfo(
            id = "game",
            title = "Game",
            imageUrl = "https://img.nvidiagrid.net/apps/key-art.jpg",
            tvCardImageUrl = "https://img.nvidiagrid.net/apps/box-art.jpg;f=webp;w=1920",
        )

        assertEquals(
            "https://img.nvidiagrid.net/apps/box-art.jpg;f=webp;w=272",
            catalogCardImageUrl(game, tvProfile = true),
        )
        assertTrue(shouldOverlayCatalogCardTitle(tvProfile = true))
        assertFalse(shouldShowCatalogCardActions(tvProfile = true, controllerActionMode = false))
    }

    @Test
    fun tvAndPhysicalControllerCardsUseEnhancedFocusFrame() {
        assertTrue(
            shouldShowEnhancedControllerFocus(
                focused = true,
                tvProfile = true,
                controllerActionMode = false,
            ),
        )
        assertTrue(
            shouldShowEnhancedControllerFocus(
                focused = true,
                tvProfile = false,
                controllerActionMode = true,
            ),
        )
        assertFalse(
            shouldShowEnhancedControllerFocus(
                focused = true,
                tvProfile = false,
                controllerActionMode = false,
            ),
        )
        assertFalse(
            shouldShowEnhancedControllerFocus(
                focused = false,
                tvProfile = true,
                controllerActionMode = false,
            ),
        )
    }

    @Test
    fun controllerFocusPulseExpandsAndFadesOnlyAtTheOuterEdge() {
        assertEquals(4f, controllerFocusPulseStrokeWidthDp(0f), 0f)
        assertEquals(13f, controllerFocusPulseStrokeWidthDp(1f), 0f)
        assertTrue(controllerFocusPulseAlpha(0f) > controllerFocusPulseAlpha(0.5f))
        assertEquals(0f, controllerFocusPulseAlpha(1f), 0f)
    }

    @Test
    fun tvGameDetailsInitiallyFocusPlayWhileTouchLayoutsKeepArtworkFocus() {
        assertTrue(shouldInitiallyFocusGameDetailsPlay(tvProfile = true))
        assertFalse(shouldInitiallyFocusGameDetailsPlay(tvProfile = false))
    }

    @Test
    fun focusedGameDetailsPlayButtonGetsAnUnmistakableLiftAndEdge() {
        assertEquals(1.06f, gameDetailsPlayFocusScale(focused = true), 0f)
        assertEquals(1f, gameDetailsPlayFocusScale(focused = false), 0f)
        assertEquals(4f, gameDetailsPlayFocusBorderWidthDp(focused = true), 0f)
        assertEquals(0f, gameDetailsPlayFocusBorderWidthDp(focused = false), 0f)
    }

    @Test
    fun whiteButtonFocusTreatmentRequiresTvOrPhysicalController() {
        assertTrue(
            shouldShowControllerFocus(
                focused = true,
                tvProfile = true,
                physicalControllerConnected = false,
            ),
        )
        assertTrue(
            shouldShowControllerFocus(
                focused = true,
                tvProfile = false,
                physicalControllerConnected = true,
            ),
        )
        assertFalse(
            shouldShowControllerFocus(
                focused = true,
                tvProfile = false,
                physicalControllerConnected = false,
            ),
        )
        assertFalse(
            shouldShowControllerFocus(
                focused = false,
                tvProfile = true,
                physicalControllerConnected = true,
            ),
        )
    }

    @Test
    fun tvCatalogCardsNeverShowStoreLabels() {
        assertFalse(shouldShowGameStoreLabels(tvProfile = true, enabled = true))
        assertTrue(shouldShowGameStoreLabels(tvProfile = false, enabled = true))
        assertFalse(shouldShowGameStoreLabels(tvProfile = false, enabled = false))
    }

    @Test
    fun tvNeverShowsTouchControlsWhileMobileBehaviorIsPreserved() {
        assertFalse(
            shouldShowAndroidTouchControls(
                tvProfile = true,
                touchInputEnabled = true,
                touchControlsEnabled = true,
                suppressedByPhysicalController = false,
            ),
        )
        assertTrue(
            shouldShowAndroidTouchControls(
                tvProfile = false,
                touchInputEnabled = true,
                touchControlsEnabled = true,
                suppressedByPhysicalController = false,
            ),
        )
        assertFalse(
            shouldShowAndroidTouchControls(
                tvProfile = false,
                touchInputEnabled = true,
                touchControlsEnabled = true,
                suppressedByPhysicalController = true,
            ),
        )
    }

    @Test
    fun tvUsesStableAudioBufferingWhileMobileKeepsLowLatencyAudio() {
        assertFalse(shouldUseLowLatencyStreamAudio(androidTvProfile = true))
        assertTrue(shouldUseLowLatencyStreamAudio(androidTvProfile = false))
    }

    @Test
    fun tvSafeAreaStartsInset() {
        assertEquals(16f, AppSettings().tvSafeAreaPaddingDp, 0f)
    }

    @Test
    fun screenEdgePaddingOnlyAppliesToTvOutsideStream() {
        val settings = AppSettings(tvSafeAreaPaddingDp = 20f)

        assertEquals(20f, appContentEdgePaddingDp(settings, inStream = false, tvProfile = true), 0f)
        assertEquals(0f, appContentEdgePaddingDp(settings, inStream = true, tvProfile = true), 0f)
        assertEquals(0f, appContentEdgePaddingDp(settings, inStream = false, tvProfile = false), 0f)
    }

    @Test
    fun gameCardScaleChangesStoreRailDensity() {
        // The catalog grid now derives its columns from GridCells.Adaptive rather than from a
        // scaled column count, but posterSizeScale keeps its meaning: larger scale, fewer cards.
        val smallCards = storeRailVisibleCardCount(900f, 146f, 10f, 0.75f)
        val largeCards = storeRailVisibleCardCount(900f, 146f, 10f, 1.4f)
        assertTrue(smallCards > largeCards)
    }

    @Test
    fun catalogCardTitlesAreCaptionedOnHandheldsOnly() {
        // TV overlays the title on the artwork, so a caption row there would repeat it.
        assertTrue(shouldShowCatalogCardTitles(tvProfile = false, enabled = true))
        assertFalse(shouldShowCatalogCardTitles(tvProfile = true, enabled = true))
        assertFalse(shouldShowCatalogCardTitles(tvProfile = false, enabled = false))
        assertTrue(shouldOverlayCatalogCardTitle(tvProfile = true))
    }

    @Test
    fun localTvRemoteRequiresExplicitOptIn() {
        assertFalse(AppSettings().localTvRemoteEnabled)
    }

    @Test
    fun localTvConnectionDotNeverLeaksIntoMobileChrome() {
        assertTrue(shouldShowLocalTvConnectionDot(tvProfile = true, pairedDeviceName = "Pixel"))
        assertFalse(shouldShowLocalTvConnectionDot(tvProfile = false, pairedDeviceName = "Pixel"))
        assertFalse(shouldShowLocalTvConnectionDot(tvProfile = true, pairedDeviceName = null))
    }

    @Test
    fun tvSettingsNeverAddsASecondBackItemToTheRail() {
        assertFalse(
            shouldShowSettingsBackRail(
                tvProfile = true,
                settingsPageOpen = true,
                horizontalChrome = true,
                detailRouteOpen = true,
            ),
        )
        assertTrue(
            shouldShowSettingsBackRail(
                tvProfile = false,
                settingsPageOpen = true,
                horizontalChrome = true,
                detailRouteOpen = true,
            ),
        )
    }

    @Test
    fun batteryOptimizationIsHiddenOnlyWhenNoBatteryIsConfirmed() {
        assertFalse(shouldShowBatteryOptimization(explicitBatteryPresent = false))
        assertTrue(shouldShowBatteryOptimization(explicitBatteryPresent = true))
        assertTrue(shouldShowBatteryOptimization(explicitBatteryPresent = null))
    }

    @Test
    fun compactTvPairingHidesMainLoginContentOnlyWhenNeeded() {
        assertTrue(shouldUseDedicatedTvPairingLayout(true, true, 640f, 360f))
        assertTrue(shouldUseDedicatedTvPairingLayout(true, true, 960f, 480f))
        assertFalse(shouldUseDedicatedTvPairingLayout(true, true, 960f, 540f))
        assertFalse(shouldUseDedicatedTvPairingLayout(false, true, 400f, 720f))
        assertFalse(shouldUseDedicatedTvPairingLayout(true, false, 640f, 360f))
    }
}
