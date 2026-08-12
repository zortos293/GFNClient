package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Test

class PrintedWasteRecommendationTest {
    @Test
    fun recommendationUsesClosestRegionWhenQueueAwareChoiceExceedsOneHundredMs() {
        val closest = zone("NP-CLOSE", pingMs = 90, queuePosition = 1_000)
        val shorterQueue = zone("NP-SHORT-QUEUE", pingMs = 101, queuePosition = 0)

        assertEquals(
            closest.zoneId,
            recommendedPrintedWasteZone(listOf(closest, shorterQueue))?.zoneId,
        )
    }

    @Test
    fun recommendationUsesClosestRegionWhenEveryMeasuredRegionExceedsOneHundredMs() {
        val closest = zone("NP-CLOSE", pingMs = 105, queuePosition = 1_000)
        val shorterQueue = zone("NP-SHORT-QUEUE", pingMs = 150, queuePosition = 0)

        assertEquals(
            closest.zoneId,
            recommendedPrintedWasteZone(listOf(closest, shorterQueue))?.zoneId,
        )
    }

    @Test
    fun recommendationKeepsQueueAwareChoiceAtOneHundredMs() {
        val closest = zone("NP-CLOSE", pingMs = 95, queuePosition = 1_000)
        val shorterQueue = zone("NP-SHORT-QUEUE", pingMs = 100, queuePosition = 0)

        assertEquals(
            shorterQueue.zoneId,
            recommendedPrintedWasteZone(listOf(closest, shorterQueue))?.zoneId,
        )
    }

    @Test
    fun closestRegionUsesQueueAndZoneIdAsStableTieBreakers() {
        val longerQueue = zone("NP-A", pingMs = 120, queuePosition = 20)
        val shorterQueue = zone("NP-B", pingMs = 120, queuePosition = 10)

        assertEquals(
            shorterQueue.zoneId,
            recommendedPrintedWasteZone(listOf(longerQueue, shorterQueue))?.zoneId,
        )
    }

    private fun zone(
        zoneId: String,
        pingMs: Long,
        queuePosition: Int,
    ) = PrintedWasteZoneOption(
        zoneId = zoneId,
        zone = PrintedWasteZone(
            QueuePosition = queuePosition,
            Region = "TEST",
        ),
        routingUrl = "https://${zoneId.lowercase()}.example.test/",
        pingMs = pingMs,
    )
}
