package com.opencloudgaming.opennow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A very slow, very quiet wash of colour behind the app when no wallpaper is set.
 *
 * Without a backdrop every page was a single flat fill, which reads as unfinished on a large phone
 * and on TV. This fills that gap without competing with anything: three wide radial blooms in the
 * accent, each under 7% alpha. It is deliberately not the Absolute Cinema treatment — that is an
 * interaction effect with somewhere specific to point, and this is wallpaper.
 *
 * This used to move the blooms on a 150-second infinite transition. The movement was nearly
 * imperceptible, but Compose still redrew three screen-sized radial shaders at every display
 * vsync. On a 120 Hz phone that consumed GPU time continuously behind the Store and Settings and
 * left less frame budget for scrolling, image upload, and route motion. A fixed phase preserves the
 * same depth without keeping the whole app in a permanent animation.
 *
 * The three brushes are cached and redraw only when their size or theme colours change.
 */
@Composable
internal fun AmbientBackground(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Box(
        modifier
            .fillMaxSize()
            // drawWithCache, not drawBehind: the gradient shaders depend only on the size and the
            // accent, so they are built once per resize rather than on every parent invalidation.
            .drawWithCache {
                val radii = AMBIENT_BLOOMS.map { size.minDimension * it.radiusFactor }
                val brushes = AMBIENT_BLOOMS.mapIndexed { index, bloom ->
                    val color = if (bloom.usesSecondary) secondary else accent
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = bloom.alpha), Color.Transparent),
                        center = Offset.Zero,
                        radius = radii[index].coerceAtLeast(1f),
                    )
                }
                onDrawBehind {
                    AMBIENT_BLOOMS.forEachIndexed { index, bloom ->
                        val radius = radii[index]
                        if (radius <= 0f) return@forEachIndexed
                        val center = ambientBloomCenter(bloom, AMBIENT_STATIC_PHASE, size)
                        translate(center.x, center.y) {
                            drawCircle(brush = brushes[index], radius = radius, center = Offset.Zero)
                        }
                    }
                }
            },
    )
}

/**
 * One drifting bloom.
 *
 * [speed] is deliberately irrational relative to the others so the three never line back up into a
 * visible loop — the pattern a viewer would notice is repetition, not motion.
 */
internal data class AmbientBloom(
    val originX: Float,
    val originY: Float,
    val travelX: Float,
    val travelY: Float,
    val speed: Float,
    val phaseOffset: Float,
    val radiusFactor: Float,
    val alpha: Float,
    val usesSecondary: Boolean,
)

internal fun ambientBloomCenter(bloom: AmbientBloom, phase: Float, size: Size): Offset {
    val angle = (phase * bloom.speed + bloom.phaseOffset) * 2f * PI.toFloat()
    return Offset(
        x = size.width * (bloom.originX + bloom.travelX * sin(angle)),
        y = size.height * (bloom.originY + bloom.travelY * cos(angle)),
    )
}

/** A point in the old motion cycle where all three blooms are spread out. */
private const val AMBIENT_STATIC_PHASE = 0.22f

private val AMBIENT_BLOOMS = listOf(
    AmbientBloom(
        originX = 0.22f,
        originY = 0.18f,
        travelX = 0.10f,
        travelY = 0.07f,
        speed = 1f,
        phaseOffset = 0f,
        radiusFactor = 0.85f,
        alpha = 0.065f,
        usesSecondary = false,
    ),
    AmbientBloom(
        originX = 0.82f,
        originY = 0.34f,
        travelX = 0.09f,
        travelY = 0.10f,
        speed = 0.61f,
        phaseOffset = 0.37f,
        radiusFactor = 0.70f,
        alpha = 0.050f,
        usesSecondary = true,
    ),
    AmbientBloom(
        originX = 0.48f,
        originY = 0.88f,
        travelX = 0.12f,
        travelY = 0.06f,
        speed = 0.41f,
        phaseOffset = 0.71f,
        radiusFactor = 0.95f,
        alpha = 0.045f,
        usesSecondary = false,
    ),
)
