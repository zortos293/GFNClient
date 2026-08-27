package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryShelfLayoutTest {
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
}
