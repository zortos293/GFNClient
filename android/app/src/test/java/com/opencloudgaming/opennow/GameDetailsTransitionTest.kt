package com.opencloudgaming.opennow

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class GameDetailsTransitionTest {
    private val source = Rect(left = 120f, top = 240f, right = 320f, bottom = 540f)
    private val target = Rect(left = 0f, top = 80f, right = 1_000f, bottom = 1_080f)

    @Test
    fun containerStartsAtTheActivatedCardBounds() {
        val transform = gameDetailsContainerTransform(source, target, progress = 0f)

        assertEquals(0.2f, transform.scaleX, 0f)
        assertEquals(0.3f, transform.scaleY, 0f)
        assertEquals(120f, transform.translationX, 0f)
        assertEquals(160f, transform.translationY, 0f)
    }

    @Test
    fun containerFinishesAtItsFullDetailsBounds() {
        val transform = gameDetailsContainerTransform(source, target, progress = 1f)

        assertEquals(1f, transform.scaleX, 0f)
        assertEquals(1f, transform.scaleY, 0f)
        assertEquals(0f, transform.translationX, 0f)
        assertEquals(0f, transform.translationY, 0f)
    }

    @Test
    fun containerClampsOutOfRangeAnimationProgress() {
        assertEquals(
            gameDetailsContainerTransform(source, target, progress = 0f),
            gameDetailsContainerTransform(source, target, progress = -1f),
        )
        assertEquals(
            gameDetailsContainerTransform(source, target, progress = 1f),
            gameDetailsContainerTransform(source, target, progress = 2f),
        )
    }

    @Test
    fun transitionRegistryKeepsCardAndHeroSourcesDistinct() {
        val registry = GameDetailsTransitionRegistry()

        registry.record("card", source, GameDetailsTransitionKind.Card)
        assertEquals(GameDetailsTransitionKind.Card, registry.originFor("card")?.kind)
        assertEquals(source, registry.originFor("card")?.bounds)

        registry.record("hero", target, GameDetailsTransitionKind.Hero)

        assertEquals(GameDetailsTransitionKind.Hero, registry.originFor("hero")?.kind)
        assertEquals(target, registry.originFor("hero")?.bounds)
        assertEquals(null, registry.originFor("card"))
    }
}
