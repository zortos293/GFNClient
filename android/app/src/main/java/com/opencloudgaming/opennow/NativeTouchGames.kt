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
 * The catalog carries the answer: a variant's `supportedControls` includes `TOUCHSCREEN`, which is
 * the same capability signal used by the official client. Using that signal avoids a title-based
 * allowlist that breaks for localized names and needs manual updates for every new touch game.
 */
/** The value the catalog uses to mark a touch-capable variant. */
internal const val SUPPORTED_CONTROL_TOUCHSCREEN = "TOUCHSCREEN"

/** Whether the catalog itself claims this game takes touch, across any of its variants. */

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

internal fun normalizeGameTitleForMatching(title: String): String =
    title.lowercase(Locale.US).filter { it.isLetterOrDigit() }

internal fun catalogClaimsTouchSupport(game: GameInfo): Boolean {
    if (normalizeGameTitleForMatching(game.title) in NATIVE_TOUCH_GAME_TITLES) return true
    return game.variants.any { variant ->
        variant.supportedControls.any { it.equals(SUPPORTED_CONTROL_TOUCHSCREEN, ignoreCase = true) }
    }
}

/** Mirrors web's `getSupportedControlsForVariant` — uses the first variant with non-empty controls. */
internal fun effectiveSupportedControls(game: GameInfo): List<String> =
    game.variants.firstNotNullOfOrNull { variant ->
        variant.supportedControls.takeIf { it.isNotEmpty() }
    } ?: emptyList()

internal fun nativeTouchModeLabel(mode: NativeTouchMode): String = when (mode) {
    NativeTouchMode.Auto -> "Supported games"
    NativeTouchMode.Off -> "Off"
    NativeTouchMode.Always -> "Every game"
}

internal fun shouldUseNativeTouch(mode: NativeTouchMode, game: GameInfo?): Boolean = when (mode) {
    NativeTouchMode.Off -> false
    NativeTouchMode.Always -> true
    NativeTouchMode.Auto -> game != null && catalogClaimsTouchSupport(game)
}

/**
 * Auto mode yields to the native desktop allocation required by high-resolution or high-FPS
 * streams. An explicit Always selection still wins when the user values native touch over that
 * stream mode.
 */
internal fun shouldUseNativeTouch(
    mode: NativeTouchMode,
    game: GameInfo?,
    streamSettings: StreamSettings,
): Boolean = shouldUseNativeTouch(mode, game) &&
    (mode == NativeTouchMode.Always ||
        !streamSettings.requiresNativeDesktopCloudMatchMode() ||
        (game != null && catalogClaimsTouchSupport(game)))

/** One line per session showing the catalog signal and the resulting native-touch decision. */
internal fun nativeTouchDiagnostics(game: GameInfo, enabled: Boolean): String {
    val controls = game.variants
        .flatMap { it.supportedControls }
        .distinct()
        .joinToString("|")
        .ifBlank { "none" }
    return "native touch enabled=$enabled id=${game.id} title=${game.title} " +
        "catalogTouch=${catalogClaimsTouchSupport(game)} " +
        "supportedControls=$controls"
}
