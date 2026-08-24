package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup paints the cached catalogue before any network work. These pin what "usable cache" means
 * and that the primed snapshot is matched to the query that will actually be run.
 */
class CatalogCachePrimingTest {
    private fun game(id: String) = GameInfo(id = id, title = id)

    private fun key(
        search: String = "",
        sort: String = CATALOG_SORT_DEFAULT,
        filters: List<String> = emptyList(),
    ) = CatalogCacheKey.of("user", "https://example.test", search, sort, filters)

    private fun snapshot(
        key: CatalogCacheKey = key(),
        main: List<GameInfo>? = null,
        library: List<GameInfo>? = null,
        catalog: CatalogBrowseResult? = null,
    ) = CatalogCacheSnapshot(key, main, library, catalog)

    @Test
    fun theDefaultStoreViewIsNotAScopedQuery() {
        assertEquals("most_popular", CATALOG_SORT_DEFAULT)
        assertFalse(isScopedCatalogQuery("", CATALOG_SORT_DEFAULT, emptyList()))
        assertTrue(isScopedCatalogQuery("", "relevance", emptyList()))
    }

    @Test
    fun search_filters_andSortEachScopeTheQuery() {
        assertTrue(isScopedCatalogQuery("halo", CATALOG_SORT_DEFAULT, emptyList()))
        assertTrue(isScopedCatalogQuery("", CATALOG_SORT_DEFAULT, listOf("genre-action")))
        assertTrue(isScopedCatalogQuery("", "latest", emptyList()))
    }

    @Test
    fun aCachedCatalogIsShownStraightAway() {
        val cached = CatalogBrowseResult(listOf(game("a"), game("b")))
        assertEquals(listOf("a", "b"), primedStoreGames(snapshot(catalog = cached)).map { it.id })
    }

    @Test
    fun theUnscopedStoreFallsBackToTheMainCache() {
        assertEquals(listOf("a"), primedStoreGames(snapshot(main = listOf(game("a")))).map { it.id })
    }

    @Test
    fun aScopedQueryWillNotBorrowTheUnscopedCache() {
        // Default-ordered games under a user-chosen sort would be visibly wrong, not just stale.
        val scoped = snapshot(key = key(sort = "latest"), main = listOf(game("a")))
        assertTrue(primedStoreGames(scoped).isEmpty())
    }

    @Test
    fun aScopedQueryStillUsesItsOwnCachedResult() {
        val scoped = snapshot(
            key = key(sort = "latest"),
            main = listOf(game("a")),
            catalog = CatalogBrowseResult(listOf(game("z"))),
        )
        assertEquals(listOf("z"), primedStoreGames(scoped).map { it.id })
    }

    @Test
    fun anEmptyCacheOffersNothingToPaint() {
        assertTrue(primedStoreGames(snapshot()).isEmpty())
        assertTrue(primedStoreGames(snapshot(main = emptyList())).isEmpty())
    }

    @Test
    fun filterOrderDoesNotMakeThePrimedSnapshotMiss() {
        // The store keys on sorted filters, so the in-memory handoff has to agree or startup
        // silently reparses megabytes of JSON it already has.
        assertEquals(key(filters = listOf("a", "b")), key(filters = listOf("b", "a")))
    }

    @Test
    fun aDifferentQueryDoesNotReuseThePrimedSnapshot() {
        assertNotEquals(key(), key(sort = "latest"))
        assertNotEquals(key(), key(search = "halo"))
        assertNotEquals(key(), key(filters = listOf("genre-action")))
    }
}
