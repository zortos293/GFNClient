package com.opencloudgaming.opennow

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogCacheStoreTest {
    @Test
    fun compressedPopularCatalogSurvivesAStoreRecreation() {
        val directory = Files.createTempDirectory("opennow-catalog-cache").toFile()
        try {
            val games = (0 until 360).map { index ->
                GameInfo(
                    id = "popular-$index",
                    title = "Popular game $index",
                    longDescription = "compressible catalog metadata ".repeat(180),
                )
            }
            CatalogCacheStore(directory).saveCatalog(
                userId = "user",
                providerStreamingBaseUrl = "https://example.test",
                searchQuery = "",
                sortId = DEFAULT_CATALOG_SORT_ID,
                filterIds = emptyList(),
                result = CatalogBrowseResult(games),
            )

            val restored = CatalogCacheStore(directory).loadCatalog(
                userId = "user",
                providerStreamingBaseUrl = "https://example.test",
                searchQuery = "",
                sortId = DEFAULT_CATALOG_SORT_ID,
                filterIds = emptyList(),
            )

            assertEquals(360, restored?.games?.size)
            assertTrue(directory.listFiles().orEmpty().any { it.extension == "gz" })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun catalogFilesRemainSpecificToTheirSort() {
        val directory = Files.createTempDirectory("opennow-catalog-cache-sort").toFile()
        try {
            val store = CatalogCacheStore(directory)
            store.saveCatalog(
                "user",
                "base",
                "",
                DEFAULT_CATALOG_SORT_ID,
                emptyList(),
                CatalogBrowseResult(listOf(game("popular"))),
            )
            store.saveCatalog(
                "user",
                "base",
                "",
                "relevance",
                emptyList(),
                CatalogBrowseResult(listOf(game("relevance"))),
            )

            val popular = store.loadCatalog("user", "base", "", DEFAULT_CATALOG_SORT_ID, emptyList())
            val relevance = store.loadCatalog("user", "base", "", "relevance", emptyList())
            assertEquals("popular", popular?.games?.single()?.id)
            assertEquals("relevance", relevance?.games?.single()?.id)
            assertEquals(2, store.clear())
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun game(id: String) = GameInfo(id = id, title = id)
}
