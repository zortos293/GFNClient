package com.opencloudgaming.opennow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Everything the on-screen controller needs to paint one control.
 *
 * The button composables used to each carry their own `if (style == V2)` ladder, which meant a new
 * skin was a six-file edit and the skins could — and did — drift out of agreement with each other.
 * They now read a single palette resolved once per frame.
 */
internal data class TouchSkinColors(
    val fill: Color,
    val pressedFill: Color,
    val border: Color,
    val pressedBorder: Color,
    val borderWidth: Dp,
    val pressedBorderWidth: Dp,
    val glyph: Color,
    val pressedGlyph: Color,
    /** The ring a stick travels inside. */
    val stickTrack: Color,
    val stickKnob: Color,
    /** Null draws the knob as a plain disc. */
    val stickKnobBorder: Color?,
    /** [Color.Transparent] leaves the d-pad cross unfilled and outline-only. */
    val dpadFill: Color,
) {
    fun fillFor(pressed: Boolean): Color = if (pressed) pressedFill else fill

    fun borderFor(pressed: Boolean): Color = if (pressed) pressedBorder else border

    fun borderWidthFor(pressed: Boolean): Dp = if (pressed) pressedBorderWidth else borderWidth

    fun glyphFor(pressed: Boolean): Color = if (pressed) pressedGlyph else glyph
}

/** Used when [AndroidTouchSettings.touchSkinTint] is unset. */
internal fun defaultTouchSkinAccent(style: TouchControllerStyle): Color = when (style) {
    TouchControllerStyle.Neon -> Color(0xff42c9ff)
    TouchControllerStyle.Retro -> Color(0xffffb020)
    TouchControllerStyle.Frost -> Color(0xffdff1ff)
    TouchControllerStyle.V1, TouchControllerStyle.V2, TouchControllerStyle.Contrast -> Color.White
}

internal fun touchSkinAccent(settings: AndroidTouchSettings): Color =
    settings.touchSkinTint
        ?.let { Color(it.r.coerceIn(0, 255), it.g.coerceIn(0, 255), it.b.coerceIn(0, 255)) }
        ?: defaultTouchSkinAccent(settings.touchControllerStyle)

/**
 * [opacity] is the reader's own slider and multiplies every alpha here, so a skin's relative
 * contrast survives being faded — a skin never hard-codes a final alpha.
 */
internal fun touchSkinColors(
    style: TouchControllerStyle,
    opacity: Float,
    accent: Color,
): TouchSkinColors {
    val alpha = opacity.coerceIn(0f, 1f)
    fun white(a: Float) = Color.White.copy(alpha = alpha * a)
    fun black(a: Float) = Color.Black.copy(alpha = alpha * a)
    fun tint(a: Float) = accent.copy(alpha = alpha * a)
    return when (style) {
        TouchControllerStyle.V1 -> TouchSkinColors(
            fill = black(0.6f),
            pressedFill = white(0.2f),
            border = white(0.4f),
            pressedBorder = white(0.4f),
            borderWidth = 1.dp,
            pressedBorderWidth = 1.dp,
            glyph = white(0.9f),
            pressedGlyph = white(1f),
            stickTrack = white(0.3f),
            stickKnob = Color.LightGray.copy(alpha = alpha * 0.8f),
            stickKnobBorder = null,
            dpadFill = black(0.6f),
        )
        TouchControllerStyle.V2 -> TouchSkinColors(
            fill = Color.Transparent,
            pressedFill = white(0.15f),
            border = white(0.5f),
            pressedBorder = white(0.9f),
            borderWidth = 1.dp,
            pressedBorderWidth = 2.dp,
            glyph = white(0.9f),
            pressedGlyph = white(1f),
            stickTrack = white(0.3f),
            stickKnob = white(0.2f),
            stickKnobBorder = white(0.5f),
            dpadFill = Color.Transparent,
        )
        TouchControllerStyle.Neon -> TouchSkinColors(
            fill = black(0.28f),
            pressedFill = tint(0.34f),
            border = tint(0.85f),
            pressedBorder = tint(1f),
            borderWidth = 2.dp,
            pressedBorderWidth = 3.dp,
            glyph = tint(0.95f),
            pressedGlyph = white(1f),
            stickTrack = tint(0.5f),
            stickKnob = tint(0.32f),
            stickKnobBorder = tint(0.9f),
            dpadFill = black(0.28f),
        )
        TouchControllerStyle.Frost -> TouchSkinColors(
            fill = white(0.14f),
            pressedFill = white(0.34f),
            border = white(0.3f),
            pressedBorder = white(0.62f),
            borderWidth = 1.dp,
            pressedBorderWidth = 2.dp,
            glyph = white(0.86f),
            pressedGlyph = white(1f),
            stickTrack = white(0.26f),
            stickKnob = white(0.3f),
            stickKnobBorder = white(0.44f),
            dpadFill = white(0.14f),
        )
        TouchControllerStyle.Contrast -> TouchSkinColors(
            fill = black(0.9f),
            pressedFill = white(0.9f),
            border = white(0.96f),
            pressedBorder = white(1f),
            borderWidth = 2.dp,
            pressedBorderWidth = 3.dp,
            glyph = white(1f),
            // The cap inverts under a finger, so the glyph has to invert with it.
            pressedGlyph = black(1f),
            stickTrack = white(0.8f),
            stickKnob = white(0.9f),
            stickKnobBorder = black(0.7f),
            dpadFill = black(0.9f),
        )
        TouchControllerStyle.Retro -> TouchSkinColors(
            fill = tint(0.82f),
            pressedFill = tint(1f),
            border = black(0.55f),
            pressedBorder = black(0.75f),
            borderWidth = 2.dp,
            pressedBorderWidth = 2.dp,
            glyph = black(0.86f),
            pressedGlyph = black(1f),
            stickTrack = tint(0.6f),
            stickKnob = tint(0.9f),
            stickKnobBorder = black(0.6f),
            dpadFill = tint(0.82f),
        )
    }
}

internal val LocalTouchSkin = androidx.compose.runtime.staticCompositionLocalOf {
    touchSkinColors(TouchControllerStyle.V1, opacity = 0.82f, accent = Color.White)
}

/** Off blanks the caps; the d-pad arrowheads stay, since a blank cross is unusable. */
internal val LocalTouchButtonLabels = androidx.compose.runtime.staticCompositionLocalOf { true }

/** The movable cap stays independently adjustable without making every stick call site carry it. */
internal val LocalTouchStickKnobScale = androidx.compose.runtime.staticCompositionLocalOf { 0.44f }

@Composable
internal fun touchControllerStyleLabel(style: TouchControllerStyle): String = when (style) {
    TouchControllerStyle.V1 -> "Solid"
    TouchControllerStyle.V2 -> "Outline"
    TouchControllerStyle.Neon -> "Neon"
    TouchControllerStyle.Frost -> "Frost"
    TouchControllerStyle.Contrast -> "High contrast"
    TouchControllerStyle.Retro -> "Retro"
}

internal fun nextTouchControllerStyle(current: TouchControllerStyle): TouchControllerStyle {
    val all = TouchControllerStyle.entries
    return all[(all.indexOf(current) + 1) % all.size]
}

internal data class TouchSkinTintOption(
    val id: String,
    val label: String,
    /** Null means "whatever the chosen skin ships with" — see [defaultTouchSkinAccent]. */
    val rgb: ControllerThemeRgb?,
)

internal const val TOUCH_SKIN_TINT_DEFAULT_ID = "default"

/**
 * A short preset list rather than a full colour wheel.
 *
 * These are picked in the stream overlay, one-handed, often mid-game and often on a controller,
 * where a hue/saturation surface is the wrong instrument.
 */
internal val TOUCH_SKIN_TINTS: List<TouchSkinTintOption> = listOf(
    TouchSkinTintOption(TOUCH_SKIN_TINT_DEFAULT_ID, "Skin default", null),
    TouchSkinTintOption("white", "White", ControllerThemeRgb(255, 255, 255)),
    TouchSkinTintOption("cyan", "Cyan", ControllerThemeRgb(66, 201, 255)),
    TouchSkinTintOption("green", "Green", ControllerThemeRgb(124, 241, 177)),
    TouchSkinTintOption("amber", "Amber", ControllerThemeRgb(255, 176, 32)),
    TouchSkinTintOption("orange", "Orange", ControllerThemeRgb(255, 106, 43)),
    TouchSkinTintOption("magenta", "Magenta", ControllerThemeRgb(255, 92, 190)),
    TouchSkinTintOption("violet", "Violet", ControllerThemeRgb(166, 133, 255)),
    TouchSkinTintOption("red", "Red", ControllerThemeRgb(255, 82, 82)),
)

/** Falls back to the default entry for a colour saved by a build that offered a wider list. */
internal fun touchSkinTintId(tint: ControllerThemeRgb?): String =
    TOUCH_SKIN_TINTS.firstOrNull { it.rgb == tint }?.id ?: TOUCH_SKIN_TINT_DEFAULT_ID

internal fun touchSkinTintForId(id: String): ControllerThemeRgb? =
    TOUCH_SKIN_TINTS.firstOrNull { it.id == id }?.rgb

internal fun nextTouchSkinTint(current: ControllerThemeRgb?): ControllerThemeRgb? {
    val currentIndex = TOUCH_SKIN_TINTS.indexOfFirst { it.rgb == current }.coerceAtLeast(0)
    return TOUCH_SKIN_TINTS[(currentIndex + 1) % TOUCH_SKIN_TINTS.size].rgb
}

internal fun touchSkinTintLabel(current: ControllerThemeRgb?): String =
    TOUCH_SKIN_TINTS.firstOrNull { it.rgb == current }?.label ?: TOUCH_SKIN_TINTS.first().label
