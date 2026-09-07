package com.opencloudgaming.opennow

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sign
import kotlin.random.Random

internal const val MASCOT_SIZE_DP = 45f

internal fun normalizeMascotDelaySeconds(seconds: Int): Int =
    ((seconds.coerceIn(5, 300) + 2) / 5) * 5

internal fun nextMascotMessage(current: Int, count: Int, random: Random = Random.Default): Int =
    if (count < 2) 0 else (current + 1 + random.nextInt(count - 1)) % count

/** Positions and speed are in dp so the mascot behaves consistently across display densities. */
internal class MascotMotion(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
) {
    fun randomizeVelocity(random: Random = Random.Default) {
        val angle = PI.toFloat() / 9f + random.nextFloat() * PI.toFloat() * 5f / 18f
        val speed = 260f + random.nextFloat() * 160f
        vx = cos(angle) * speed * if (random.nextBoolean()) 1f else -1f
        vy = sin(angle) * speed * if (random.nextBoolean()) 1f else -1f
    }

    /** A corner is one bounce. Clamp long frames so returning to the app cannot teleport it. */
    fun advance(width: Float, height: Float, elapsedSeconds: Float, random: Random = Random.Default): Boolean {
        val maxX = (width - MASCOT_SIZE_DP).coerceAtLeast(0f)
        val maxY = (height - MASCOT_SIZE_DP).coerceAtLeast(0f)
        val dt = elapsedSeconds.coerceIn(0f, 0.05f)
        x += vx * dt
        y += vy * dt
        val hitX = maxX > 0f && ((x <= 0f && vx < 0f) || (x >= maxX && vx > 0f))
        val hitY = maxY > 0f && ((y <= 0f && vy < 0f) || (y >= maxY && vy > 0f))
        x = x.coerceIn(0f, maxX)
        y = y.coerceIn(0f, maxY)
        if (!hitX && !hitY) return false
        val directionX = if (hitX) (if (x == 0f) 1f else -1f) else sign(vx)
        val directionY = if (hitY) (if (y == 0f) 1f else -1f) else sign(vy)
        randomizeVelocity(random)
        vx = abs(vx) * directionX
        vy = abs(vy) * directionY
        return true
    }
}
