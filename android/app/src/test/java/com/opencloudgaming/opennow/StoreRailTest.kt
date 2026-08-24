package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreRailTest {
    @Test
    fun storeHeroIsLimitedToPortraitPhones() {
        assertTrue(shouldShowStoreHero(tvProfile = false, landscape = false))
        assertFalse(shouldShowStoreHero(tvProfile = false, landscape = true))
        assertFalse(shouldShowStoreHero(tvProfile = true, landscape = false))
    }

    @Test
    fun storeHeroPreservesTheNewGamesAddedProviderOrder() {
        val newest = GameInfo(id = "newest", title = "Newest", imageUrl = "poster")
        val next = GameInfo(id = "next", title = "Next", screenshotUrl = "wide")

        val result = newlyAddedStoreHeroGames(listOf(newest, next))

        assertEquals(listOf("newest", "next"), result.map(GameInfo::id))
    }

    @Test
    fun storeHeroDeduplicatesVariantsWithoutAddingOtherCatalogGames() {
        val steam = GameInfo(id = "same", title = "Game", availableStores = listOf("Steam"))
        val epic = steam.copy(availableStores = listOf("Epic Games Store"))

        val result = newlyAddedStoreHeroGames(listOf(steam, epic))

        assertEquals(1, result.size)
        assertEquals("same", result.single().id)
    }

    @Test
    fun storeHeroDoesNotRepeatGamesAlreadyInPersonalRails() {
        val recent = GameInfo(id = "recent", title = "Recent")
        val actuallyNew = GameInfo(id = "new", title = "Actually new")

        val result = newlyAddedStoreHeroGames(
            games = listOf(recent, actuallyNew),
            excludedGames = listOf(recent),
        )

        assertEquals(listOf("new"), result.map(GameInfo::id))
    }

    @Test
    fun newGamesAddedFeedIsOnlyAnUnfilteredProviderSort() {
        assertTrue(isNewlyAddedCatalogQuery("", NEWLY_ADDED_CATALOG_SORT_ID, emptyList()))
        assertTrue(isNewlyAddedCatalogQuery("", "latest", emptyList()))
        assertFalse(isNewlyAddedCatalogQuery("halo", NEWLY_ADDED_CATALOG_SORT_ID, emptyList()))
        assertFalse(isNewlyAddedCatalogQuery("", NEWLY_ADDED_CATALOG_SORT_ID, listOf("genre-action")))
    }

    @Test
    fun wideArtworkRequestsAreBoundedByTheDisplay() {
        assertEquals(960, boundedWideImageRequestWidth(networkWidth = 1920, displayWidth = 720))
        assertEquals(1280, boundedWideImageRequestWidth(networkWidth = 1920, displayWidth = 1080))
        assertEquals(1920, boundedWideImageRequestWidth(networkWidth = 1920, displayWidth = 1920))
    }

    @Test
    fun slowNetworkArtworkRequestsAreNotUpscaledToTheDisplay() {
        assertEquals(960, boundedWideImageRequestWidth(networkWidth = 960, displayWidth = 1440))
    }

    @Test
    fun phoneGridArtworkUsesDisplaySizedBuckets() {
        assertEquals(256, catalogCardImageRequestWidth(cardWidthPx = 220, tvProfile = false))
        assertEquals(384, catalogCardImageRequestWidth(cardWidthPx = 300, tvProfile = false))
        assertEquals(512, catalogCardImageRequestWidth(cardWidthPx = 420, tvProfile = false))
        assertEquals(640, catalogCardImageRequestWidth(cardWidthPx = 540, tvProfile = false))
        assertEquals(272, catalogCardImageRequestWidth(cardWidthPx = 540, tvProfile = true))
    }

    @Test
    fun storeCacheWindowScalesWithTheDisplayRefreshRate() {
        assertEquals(0.33f to 0.17f, catalogCacheWindowFractions(60f))
        assertEquals(0.67f to 0.33f, catalogCacheWindowFractions(90f))
        assertEquals(1f to 0.5f, catalogCacheWindowFractions(120f))
    }
}
