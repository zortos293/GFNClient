package com.opencloudgaming.opennow

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sign
import kotlin.random.Random

class MascotMotionTest {
    @Test
    fun eachEdgeAndCornerReflectsInward() {
        val random = Random(42)
        val cases = listOf(
            listOf(0f, 50f, -300f, 200f, 1f, 1f),
            listOf(155f, 50f, 300f, 200f, -1f, 1f),
            listOf(50f, 0f, 300f, -200f, 1f, 1f),
            listOf(50f, 155f, 300f, 200f, 1f, -1f),
            listOf(155f, 155f, 300f, 200f, -1f, -1f),
        )
        cases.forEach { values ->
            val motion = MascotMotion(values[0], values[1], values[2], values[3])
            assertTrue(motion.advance(200f, 200f, 0.016f, random))
            assertEquals(values[4], sign(motion.vx))
            assertEquals(values[5], sign(motion.vy))
            assertTrue(motion.x in 0f..155f && motion.y in 0f..155f)
        }
    }

    @Test
    fun motionSurvivesResizesTinyWindowsAndLongFrames() {
        val random = Random(42)
        val motion = MascotMotion(800f, 600f, 350f, -250f)
        repeat(2_000) { index ->
            val width = if (index % 300 < 10) 30f else 500f
            val height = if (index % 300 < 10) 20f else 300f
            motion.advance(width, height, if (index % 100 == 0) 20f else 1f / 60f, random)
            assertTrue(motion.x in 0f..(width - MASCOT_SIZE_DP).coerceAtLeast(0f))
            assertTrue(motion.y in 0f..(height - MASCOT_SIZE_DP).coerceAtLeast(0f))
            assertTrue(motion.vx.isFinite() && motion.vy.isFinite())
        }
    }

    @Test
    fun movementAwayFromWallsIsContinuousAndDoesNotChangeDirection() {
        val motion = MascotMotion(100f, 100f, 300f, -200f)
        assertFalse(motion.advance(500f, 500f, 0.02f))
        assertEquals(106f, motion.x, 0.001f)
        assertEquals(96f, motion.y, 0.001f)
        assertEquals(300f, motion.vx, 0f)
        assertEquals(-200f, motion.vy, 0f)
    }

    @Test
    fun everyBounceGetsADifferentMessage() {
        val random = Random(123)
        var current = 0
        repeat(1_000) {
            val next = nextMascotMessage(current, 5, random)
            assertNotEquals(current, next)
            assertTrue(next in 0..4)
            current = next
        }
    }

    @Test
    fun mascotDefaultsAndSettingsSurviveSerialization() {
        val legacy = OpenNowJson.decodeFromString<AppSettings>("{}")
        assertFalse(legacy.uselessMascotEnabled)
        assertEquals(5, legacy.uselessMascotDelaySeconds)
        val enabled = legacy.copy(uselessMascotEnabled = true, uselessMascotDelaySeconds = 30)
        assertEquals(enabled, OpenNowJson.decodeFromString<AppSettings>(OpenNowJson.encodeToString(enabled)))
        for (value in listOf(Int.MIN_VALUE, -1, 0, 4, 5, 12, 299, 300, Int.MAX_VALUE)) {
            val normalized = enabled.copy(uselessMascotDelaySeconds = value).normalizedForAndroid()
            assertTrue(normalized.uselessMascotDelaySeconds in 5..300)
            assertEquals(0, normalized.uselessMascotDelaySeconds % 5)
            assertEquals(normalized, normalized.normalizedForAndroid())
        }
    }
}
