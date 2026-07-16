package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidTvUiBehaviorTest {
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
    fun tvSafeAreaStartsInset() {
        assertEquals(16f, AppSettings().tvSafeAreaPaddingDp, 0f)
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
    fun compactTvPairingHidesMainLoginContentOnlyWhenNeeded() {
        assertTrue(shouldUseDedicatedTvPairingLayout(true, true, 640f, 360f))
        assertTrue(shouldUseDedicatedTvPairingLayout(true, true, 960f, 480f))
        assertFalse(shouldUseDedicatedTvPairingLayout(true, true, 960f, 540f))
        assertFalse(shouldUseDedicatedTvPairingLayout(false, true, 400f, 720f))
        assertFalse(shouldUseDedicatedTvPairingLayout(true, false, 640f, 360f))
    }
}
