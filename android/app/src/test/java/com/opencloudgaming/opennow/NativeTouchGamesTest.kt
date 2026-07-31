package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Native touch reroutes every finger away from the cursor and gamepad paths, so *where* it switches
 * on is a safety question, not a preference one. These tests pin the two halves of that: the listed
 * games get it, and nothing else does.
 */
class NativeTouchGamesTest {

    private fun game(
        id: String = "id-1",
        title: String = "Some Game",
        supportedControls: List<String> = emptyList(),
    ) = GameInfo(
        id = id,
        title = title,
        variants = listOf(GameVariant(id = "v1", store = "STEAM", supportedControls = supportedControls)),
    )

    @Test
    fun listedGamesGetNativeTouch() {
        assertTrue(isKnownNativeTouchGame(game(title = "Genshin Impact")))
        assertTrue(isKnownNativeTouchGame(game(title = "NTE: Neverness to Everness")))
        assertTrue(isKnownNativeTouchGame(game(title = "Papers, Please")))
        assertTrue(isKnownNativeTouchGame(game(title = "LEGO Fortnite Odyssey")))
    }

    /** Catalog titles vary in case and punctuation; none of that changes which game it is. */
    @Test
    fun titleMatchingIgnoresCaseAndPunctuation() {
        assertTrue(isKnownNativeTouchGame(game(title = "papers please")))
        assertTrue(isKnownNativeTouchGame(game(title = "SLAY THE SPIRE")))
        assertTrue(isKnownNativeTouchGame(game(title = "Into  the   Breach")))
    }

    /** The Fortnite entries are separate products; matching must not bleed between them. */
    @Test
    fun eachFortniteEntryIsItsOwnGame() {
        assertTrue(isKnownNativeTouchGame(game(title = "Fortnite")))
        assertTrue(isKnownNativeTouchGame(game(title = "Fortnite Festival")))
        assertFalse(isKnownNativeTouchGame(game(title = "Fortnite Save the World")))
    }

    @Test
    fun unlistedGamesDoNotGetNativeTouch() {
        assertFalse(isKnownNativeTouchGame(game(title = "Cyberpunk 2077")))
        assertFalse(isKnownNativeTouchGame(game(title = "Counter-Strike 2")))
    }

    /**
     * The catalog's own TOUCHSCREEN flag is recorded but deliberately not acted on yet — a server
     * value must not be able to switch this on for a game nobody has tried.
     */
    @Test
    fun theCatalogTouchFlagAloneDoesNotEnableIt() {
        val unlistedButFlagged = game(title = "Cyberpunk 2077", supportedControls = listOf("TOUCHSCREEN"))
        assertTrue(catalogClaimsTouchSupport(unlistedButFlagged))
        assertFalse(isKnownNativeTouchGame(unlistedButFlagged))
        assertFalse(shouldUseNativeTouch(NativeTouchMode.Auto, unlistedButFlagged))
    }

    @Test
    fun catalogTouchFlagIsCaseInsensitive() {
        assertTrue(catalogClaimsTouchSupport(game(supportedControls = listOf("Touchscreen"))))
        assertFalse(catalogClaimsTouchSupport(game(supportedControls = listOf("X_INPUT_GAMEPAD"))))
        assertFalse(catalogClaimsTouchSupport(game()))
    }

    // -- Mode ---------------------------------------------------------------------------------

    @Test
    fun autoFollowsTheList() {
        assertTrue(shouldUseNativeTouch(NativeTouchMode.Auto, game(title = "Genshin Impact")))
        assertFalse(shouldUseNativeTouch(NativeTouchMode.Auto, game(title = "Cyberpunk 2077")))
    }

    @Test
    fun offWinsOverEverything() {
        assertFalse(shouldUseNativeTouch(NativeTouchMode.Off, game(title = "Genshin Impact")))
    }

    @Test
    fun alwaysAppliesToUnlistedGamesToo() {
        assertTrue(shouldUseNativeTouch(NativeTouchMode.Always, game(title = "Cyberpunk 2077")))
    }

    /** No game means no session to route touches into; Auto must not guess. */
    @Test
    fun autoStaysOffWithoutAGame() {
        assertFalse(shouldUseNativeTouch(NativeTouchMode.Auto, null))
    }

    @Test
    fun theModeDefaultsToAuto() {
        assertEquals(NativeTouchMode.Auto, AndroidTouchSettings().nativeTouchMode)
    }

    @Test
    fun diagnosticsCarryWhatIsNeededToFillTheList() {
        val line = nativeTouchDiagnostics(
            game(id = "abc123", title = "Genshin Impact", supportedControls = listOf("TOUCHSCREEN", "KEYBOARD")),
            enabled = true,
        )
        assertTrue(line, line.contains("id=abc123"))
        assertTrue(line, line.contains("title=Genshin Impact"))
        assertTrue(line, line.contains("TOUCHSCREEN"))
        assertTrue(line, line.contains("listed=true"))
    }
}
