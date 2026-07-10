package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreRailTest {
    @Test
    fun comingNextUsesOnlyGamesFromNvidiaNewOrUpdatedSections() {
        val newGame = GameInfo(
            id = "new-game",
            title = "New Game",
            catalogSectionTitle = "New games this week",
        )
        val updatedGame = GameInfo(
            id = "updated-game",
            title = "Updated Game",
            catalogSectionTitle = "Recently updated games",
        )
        val featuredGame = GameInfo(
            id = "featured-game",
            title = "Featured Game",
            catalogSectionTitle = "Featured",
        )

        val result = comingNextStoreGames(
            games = listOf(featuredGame, newGame, updatedGame),
            excludedGames = emptyList(),
        )

        assertEquals(listOf("new-game", "updated-game"), result.map(GameInfo::id))
    }

    @Test
    fun comingNextDoesNotRepeatJumpBackInGames() {
        val game = GameInfo(
            id = "new-game",
            title = "New Game",
            catalogSectionTitle = "New games this week",
        )

        val result = comingNextStoreGames(games = listOf(game), excludedGames = listOf(game))

        assertEquals(emptyList<GameInfo>(), result)
    }
}
