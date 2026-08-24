package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryShelfLayoutTest {
    private fun game(id: String, recent: String? = null) = GameInfo(
        id = id,
        title = id,
        lastPlayed = recent,
    )

    @Test
    fun collapsingTheShelfMovesTheGridsUpTargetToTheHeader() {
        // The tiles are gone when folded, and requesting focus on an uncomposed target throws.
        assertEquals(
            "header",
            libraryGridUpFocusTarget(
                shelfVisible = true,
                shelfCollapsed = true,
                shelfTile = "tile",
                shelfHeader = "header",
                topBar = "top",
            ),
        )
        assertEquals(
            "tile",
            libraryGridUpFocusTarget(
                shelfVisible = true,
                shelfCollapsed = false,
                shelfTile = "tile",
                shelfHeader = "header",
                topBar = "top",
            ),
        )
    }

    @Test
    fun hiddenShelfSendsFocusStraightToTheTopBar() {
        assertEquals(
            "top",
            libraryGridUpFocusTarget(
                shelfVisible = false,
                shelfCollapsed = false,
                shelfTile = "tile",
                shelfHeader = "header",
                topBar = "top",
            ),
        )
        assertNull(
            libraryGridUpFocusTarget(
                shelfVisible = false,
                shelfCollapsed = true,
                shelfTile = "tile",
                shelfHeader = "header",
                topBar = null,
            ),
        )
    }

    @Test
    fun heroLeadsWithFavouritesThenRecentlyPlayed() {
        val games = listOf(
            game("a"),
            game("b", recent = "2026-08-01T00:00:00Z"),
            game("c", recent = "2026-08-20T00:00:00Z"),
            game("d"),
        )
        val hero = libraryHeroGames(games, favoriteIds = listOf("d"))
        assertEquals(listOf("d", "c", "b", "a"), hero.map { it.id })
    }

    @Test
    fun heroNeverRepeatsAGameOrOutgrowsItsCap() {
        val games = (1..20).map { game("g$it", recent = "2026-08-%02d".format(it)) }
        val hero = libraryHeroGames(games, favoriteIds = listOf("g3", "g3", "g7"))
        assertEquals(LIBRARY_HERO_MAX_GAMES, hero.size)
        assertEquals(hero.size, hero.map { it.id }.distinct().size)
        assertEquals(listOf("g3", "g7"), hero.map { it.id }.take(2))
    }

    @Test
    fun heroIsPortraitOnlyAndStandsDownForSearchResults() {
        assertTrue(shouldShowLibraryHero(enabled = true, portraitPhone = true, resultsOnly = false, heroGameCount = 4))
        assertFalse(shouldShowLibraryHero(enabled = false, portraitPhone = true, resultsOnly = false, heroGameCount = 4))
        assertFalse(shouldShowLibraryHero(enabled = true, portraitPhone = false, resultsOnly = false, heroGameCount = 4))
        assertFalse(shouldShowLibraryHero(enabled = true, portraitPhone = true, resultsOnly = true, heroGameCount = 4))
    }

    @Test
    fun aSingleGameIsNotACarousel() {
        assertFalse(shouldShowLibraryHero(enabled = true, portraitPhone = true, resultsOnly = false, heroGameCount = 1))
        assertTrue(shouldShowLibraryHero(enabled = true, portraitPhone = true, resultsOnly = false, heroGameCount = 2))
    }
}
