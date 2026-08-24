package com.opencloudgaming.opennow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    /**
     * The reader's opacity slider, kept alongside the palette so the shading a [TouchSkinForm] adds
     * — gloss, glow, the rim on a domed cap — fades with everything else instead of surviving at
     * full strength on a controller the reader asked to be nearly invisible.
     */
    val opacity: Float = 1f,
) {
    fun fillFor(pressed: Boolean): Color = if (pressed) pressedFill else fill

    fun borderFor(pressed: Boolean): Color = if (pressed) pressedBorder else border

    fun borderWidthFor(pressed: Boolean): Dp = if (pressed) pressedBorderWidth else borderWidth

    fun glyphFor(pressed: Boolean): Color = if (pressed) pressedGlyph else glyph

    /** White at [strength], faded by the reader's slider. Used for gloss and rim highlights. */
    fun sheen(strength: Float): Color = Color.White.copy(alpha = (opacity * strength).coerceIn(0f, 1f))
}

/** The outline of a face button, a thumb-stick click, or a start/select cap. */
internal enum class TouchCapShape {
    Circle,

    /** A rounded square; how round is [TouchSkinForm.capCornerPercent]. */
    Rounded,

    /** Flat-topped six-sided cap — the label still sits in the wide middle. */
    Hexagon,
}

/** How the four directions of the d-pad are drawn. The hit test is angular in every case. */
internal enum class TouchDpadShape {
    /** One continuous plus, the arms joined at the hub. */
    Cross,

    /** Four separate keys with a gap between them; how round is [TouchSkinForm.dpadCornerPercent]. */
    Segmented,

    /** A single round pad with the directions grooved into it and pressed quadrants lit as pie slices. */
    Disc,

    /** Four triangular wedges radiating from a gap at the hub — the blade is its own arrowhead. */
    Blades,
}

/** How a stick's track and its travelling cap are drawn. */
internal enum class TouchStickShape {
    /** A hairline ring and a plain disc. */
    Ring,

    /** A recessed bowl with a domed cap. */
    Dish,

    /** An open sight: dashed ring, axis ticks, and a cap you can see the game through. */
    Crosshair,

    /** A six-sided gate matching a hexagonal cap set. */
    Hex,

    /** A restrictor plate: an octagon the cap corners into, with a square-ish cap. */
    Gate,

    /** A ball top on a visible shaft. */
    Ball,
}

/** How the shoulders and the thumb-click pills are cut. */
internal enum class TouchShoulderShape {
    Pill,
    Slab,

    /** Points at both ends. */
    Wedge,
}

/** The arrowheads a d-pad marks its directions with. [TouchDpadShape.Blades] needs none. */
internal enum class TouchDpadArrow {
    Triangle,
    Chevron,
    None,
}

/**
 * The silhouette half of a skin.
 *
 * A skin that only recolours the same shapes is not a skin — every style here changes what the
 * controls actually *are*: the cut of a cap, whether the d-pad is one cross or four keys, what the
 * stick travels inside. [touchSkinColors] then decides how that silhouette is painted.
 */
internal data class TouchSkinForm(
    val capShape: TouchCapShape,
    /** Corner radius of a [TouchCapShape.Rounded] cap, as a percentage of its half-extent. */
    val capCornerPercent: Int = 30,
    /** A second ring set inside the cap edge — the plunger rim of an arcade button. */
    val capRim: Boolean = false,
    val dpadShape: TouchDpadShape,
    /** Corner radius of a [TouchDpadShape.Segmented] key; 100 makes the keys round. */
    val dpadCornerPercent: Int = 24,
    val dpadArrow: TouchDpadArrow = TouchDpadArrow.Triangle,
    val stickShape: TouchStickShape,
    /** Multiplies the reader's own knob-size slider so a ball top can be a ball top. */
    val stickKnobScale: Float = 1f,
    val shoulderShape: TouchShoulderShape,
    /** Bloom drawn outside the edge. [Dp.Unspecified]-free: 0.dp draws none. */
    val glow: Dp = 0.dp,
    /** Strength of the highlight sweep that makes a cap read as domed. 0f draws none. */
    val gloss: Float = 0f,
    /** Caps shrink to this fraction under a finger. 1f keeps them still. */
    val pressScale: Float = 1f,
    val glyphFamily: FontFamily = FontFamily.Default,
    val glyphWeight: FontWeight = FontWeight.SemiBold,
    val glyphLetterSpacing: TextUnit = 0.sp,
    val glyphScale: Float = 1f,
    val glyphUppercase: Boolean = false,
) {
    /** What the styles are actually distinguished by — see `everySkinHasItsOwnSilhouette`. */
    val silhouette: List<Any>
        get() = listOf(
            capShape,
            capCornerPercent,
            capRim,
            dpadShape,
            dpadCornerPercent,
            stickShape,
            shoulderShape,
        )
}

/** Used when [AndroidTouchSettings.touchSkinTint] is unset. */
internal fun defaultTouchSkinAccent(style: TouchControllerStyle): Color = when (style) {
    TouchControllerStyle.Neon -> Color(0xff42c9ff)
    TouchControllerStyle.Retro -> Color(0xffffb020)
    TouchControllerStyle.Frost -> Color(0xffdff1ff)
    TouchControllerStyle.Arcade -> Color(0xffff4d5e)
    TouchControllerStyle.V1, TouchControllerStyle.V2, TouchControllerStyle.Contrast -> Color.White
}

internal fun touchSkinAccent(settings: AndroidTouchSettings): Color =
    settings.touchSkinTint
        ?.let { Color(it.r.coerceIn(0, 255), it.g.coerceIn(0, 255), it.b.coerceIn(0, 255)) }
        ?: defaultTouchSkinAccent(settings.touchControllerStyle)

/**
 * The shapes a style is built from. Independent of the reader's opacity and tint, which only ever
 * touch [touchSkinColors] — the silhouette does not move when someone fades the controller out.
 */
internal fun touchSkinForm(style: TouchControllerStyle): TouchSkinForm = when (style) {
    // The original, kept exactly as it was drawn: this is the one people already have muscle
    // memory for, so it gains no dome, no glow and no press travel.
    TouchControllerStyle.V1 -> TouchSkinForm(
        capShape = TouchCapShape.Circle,
        dpadShape = TouchDpadShape.Cross,
        stickShape = TouchStickShape.Ring,
        shoulderShape = TouchShoulderShape.Pill,
    )
    // A heads-up display rather than a gamepad: nothing is filled, the d-pad is a grooved ring and
    // the stick is a sight you aim through.
    TouchControllerStyle.V2 -> TouchSkinForm(
        capShape = TouchCapShape.Circle,
        dpadShape = TouchDpadShape.Disc,
        dpadArrow = TouchDpadArrow.Chevron,
        stickShape = TouchStickShape.Crosshair,
        stickKnobScale = 0.86f,
        shoulderShape = TouchShoulderShape.Pill,
        glyphWeight = FontWeight.Medium,
        glyphLetterSpacing = 1.4.sp,
        glyphScale = 0.94f,
    )
    // Hexagons, blades and bloom.
    TouchControllerStyle.Neon -> TouchSkinForm(
        capShape = TouchCapShape.Hexagon,
        dpadShape = TouchDpadShape.Blades,
        dpadArrow = TouchDpadArrow.None,
        stickShape = TouchStickShape.Hex,
        shoulderShape = TouchShoulderShape.Wedge,
        glow = 7.dp,
        pressScale = 0.96f,
        glyphWeight = FontWeight.Bold,
        glyphLetterSpacing = 1.8.sp,
        glyphUppercase = true,
    )
    // Soft glass: squircle caps, a single round pad, a stick sunk into a bowl.
    TouchControllerStyle.Frost -> TouchSkinForm(
        capShape = TouchCapShape.Rounded,
        capCornerPercent = 46,
        dpadShape = TouchDpadShape.Disc,
        stickShape = TouchStickShape.Dish,
        shoulderShape = TouchShoulderShape.Slab,
        gloss = 0.30f,
        pressScale = 0.97f,
        glyphWeight = FontWeight.Medium,
    )
    // Chunky and unambiguous: every control is a separate block with a rim around it.
    TouchControllerStyle.Contrast -> TouchSkinForm(
        capShape = TouchCapShape.Circle,
        capRim = true,
        dpadShape = TouchDpadShape.Segmented,
        dpadCornerPercent = 14,
        stickShape = TouchStickShape.Dish,
        shoulderShape = TouchShoulderShape.Slab,
        glyphWeight = FontWeight.Black,
        glyphScale = 1.08f,
    )
    // A handheld from before analogue sticks: square keys, a restrictor gate, a monospaced legend.
    TouchControllerStyle.Retro -> TouchSkinForm(
        capShape = TouchCapShape.Rounded,
        capCornerPercent = 26,
        dpadShape = TouchDpadShape.Segmented,
        dpadCornerPercent = 22,
        stickShape = TouchStickShape.Gate,
        shoulderShape = TouchShoulderShape.Slab,
        gloss = 0.20f,
        pressScale = 0.94f,
        glyphFamily = FontFamily.Monospace,
        glyphWeight = FontWeight.Bold,
        glyphLetterSpacing = 0.8.sp,
        glyphUppercase = true,
        glyphScale = 0.92f,
    )
    // A cabinet panel: domed convex buttons on chrome rims, round d-pad keys, a ball top on a shaft.
    TouchControllerStyle.Arcade -> TouchSkinForm(
        capShape = TouchCapShape.Circle,
        capRim = true,
        dpadShape = TouchDpadShape.Segmented,
        dpadCornerPercent = 100,
        stickShape = TouchStickShape.Ball,
        stickKnobScale = 1.18f,
        shoulderShape = TouchShoulderShape.Pill,
        gloss = 0.42f,
        pressScale = 0.90f,
        glyphWeight = FontWeight.Bold,
    )
}

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
    val palette = when (style) {
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
        TouchControllerStyle.Arcade -> TouchSkinColors(
            fill = tint(0.88f),
            pressedFill = tint(0.52f),
            // The rim is chrome, not ink — it is what makes the cap read as a convex plunger.
            border = white(0.82f),
            pressedBorder = white(1f),
            borderWidth = 2.dp,
            pressedBorderWidth = 2.dp,
            glyph = black(0.8f),
            pressedGlyph = black(1f),
            stickTrack = black(0.55f),
            stickKnob = tint(0.95f),
            stickKnobBorder = white(0.75f),
            dpadFill = tint(0.88f),
        )
    }
    return palette.copy(opacity = alpha)
}

internal val LocalTouchSkin = androidx.compose.runtime.staticCompositionLocalOf {
    touchSkinColors(TouchControllerStyle.V1, opacity = 0.82f, accent = Color.White)
}

internal val LocalTouchSkinForm = androidx.compose.runtime.staticCompositionLocalOf {
    touchSkinForm(TouchControllerStyle.V1)
}

/** Off blanks the caps; the d-pad arrowheads stay, since a blank cross is unusable. */
internal val LocalTouchButtonLabels = androidx.compose.runtime.staticCompositionLocalOf { true }

/** The movable cap stays independently adjustable without making every stick call site carry it. */
internal val LocalTouchStickKnobScale = androidx.compose.runtime.staticCompositionLocalOf { 0.44f }

@Composable
internal fun touchControllerStyleLabel(style: TouchControllerStyle): String = when (style) {
    TouchControllerStyle.V1 -> "Classic"
    TouchControllerStyle.V2 -> "Outline"
    TouchControllerStyle.Neon -> "Neon"
    TouchControllerStyle.Frost -> "Frost"
    TouchControllerStyle.Contrast -> "High contrast"
    TouchControllerStyle.Retro -> "Retro"
    TouchControllerStyle.Arcade -> "Arcade"
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
