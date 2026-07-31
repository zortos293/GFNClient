package com.opencloudgaming.opennow

import java.util.Locale

/**
 * Which games get native touch.
 *
 * GeForce NOW ships no per-game touch layouts. The games that "support touch" are simply the ones
 * whose Windows build already reacts to a Windows digitizer — invariably because they also ship on
 * phones or tablets — and they switch to their own mobile UI the moment one appears. So this file
 * decides nothing about *how* touch works, only *where* we turn it on.
 *
 * The catalog does carry the answer: a variant's `supportedControls` includes `TOUCHSCREEN`, and
 * that is exactly the test the official client applies. We deliberately do not act on it yet — it
 * is unverified whether the server sends that value to a client identifying as a desktop, and a
 * server-driven trigger could silently enable a whole new input path on games nobody has tried.
 * A fixed list cannot surprise anyone. [nativeTouchDiagnostics] records what the server actually
 * sends so the automatic signal can be adopted later on evidence rather than hope.
 */

/**
 * Authoritative, but starts empty: GFN app ids are not published anywhere we can read, and guessing
 * them would be worse than matching titles. Add entries from [nativeTouchDiagnostics] output.
 */
internal val NATIVE_TOUCH_GAME_IDS: Set<String> = emptySet()

/**
 * The weaker signal, and the reason the feature works before any id is known. Titles can be
 * localised, in which case a game simply will not match — the feature stays off, which is the safe
 * direction to fail in.
 */
internal val NATIVE_TOUCH_GAME_TITLES: Set<String> = setOf(
    "NTE: Neverness to Everness",
    "Genshin Impact",
    "Fortnite",
    "LEGO Fortnite Odyssey",
    "Fortnite Festival",
    "Slay the Spire",
    "Dota Underlords",
    "Into the Breach",
    "Tabletop Simulator",
    "Papers, Please",
).mapTo(mutableSetOf()) { normalizeGameTitleForMatching(it) }

/** The value the catalog uses to mark a touch-capable variant. Recorded, not yet acted on. */
internal const val SUPPORTED_CONTROL_TOUCHSCREEN = "TOUCHSCREEN"

/**
 * Punctuation, spacing and case all vary between catalog entries and the names people write down;
 * none of them distinguish one game from another.
 */
internal fun normalizeGameTitleForMatching(title: String): String =
    title.lowercase(Locale.US).filter { it.isLetterOrDigit() }

internal fun isKnownNativeTouchGame(game: GameInfo): Boolean =
    game.id in NATIVE_TOUCH_GAME_IDS ||
        normalizeGameTitleForMatching(game.title) in NATIVE_TOUCH_GAME_TITLES

/** Whether the catalog itself claims this game takes touch, across any of its variants. */
internal fun catalogClaimsTouchSupport(game: GameInfo): Boolean =
    game.variants.any { variant ->
        variant.supportedControls.any { it.equals(SUPPORTED_CONTROL_TOUCHSCREEN, ignoreCase = true) }
    }

internal fun nativeTouchModeLabel(mode: NativeTouchMode): String = when (mode) {
    NativeTouchMode.Auto -> "Supported games"
    NativeTouchMode.Off -> "Off"
    NativeTouchMode.Always -> "Every game"
}

internal fun shouldUseNativeTouch(mode: NativeTouchMode, game: GameInfo?): Boolean = when (mode) {
    NativeTouchMode.Off -> false
    NativeTouchMode.Always -> true
    NativeTouchMode.Auto -> game != null && isKnownNativeTouchGame(game)
}

/**
 * One line per session, so the fixed list above can be filled in from real data and so we can see
 * whether `supportedControls` agrees with it. Deliberately includes games we do *not* enable.
 */
internal fun nativeTouchDiagnostics(game: GameInfo, enabled: Boolean): String {
    val controls = game.variants
        .flatMap { it.supportedControls }
        .distinct()
        .joinToString("|")
        .ifBlank { "none" }
    return "native touch enabled=$enabled id=${game.id} title=${game.title} " +
        "listed=${isKnownNativeTouchGame(game)} catalogTouch=${catalogClaimsTouchSupport(game)} " +
        "supportedControls=$controls"
}
