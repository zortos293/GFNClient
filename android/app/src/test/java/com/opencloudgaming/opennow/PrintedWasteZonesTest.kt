package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintedWasteZonesTest {

    private fun zone(id: String, queue: Int, ping: Long?, region: String = "US") = PrintedWasteZoneOption(
        zoneId = id,
        zone = PrintedWasteZone(QueuePosition = queue, Region = region),
        routingUrl = printedWasteZoneUrl(id),
        pingMs = ping,
    )

    private fun entry(title: String, region: String, gpu5080: Boolean = false, gpu4080: Boolean = false) =
        PrintedWasteServerMappingEntry(
            title = title,
            region = region,
            is4080Server = gpu4080,
            is5080Server = gpu5080,
            nuked = false,
        )

    @Test
    fun alliancePartnerZonesAreNotQueueRoutable() {
        assertTrue(isStandardPrintedWasteZone("NP-LAX-03"))
        assertFalse(isStandardPrintedWasteZone("NPA-LON-01"))
        assertFalse(isStandardPrintedWasteZone("EU-LON-01"))
    }

    @Test
    fun zoneUrlUsesTheLowercasedIdOnCloudMatch() {
        assertEquals("https://np-lax-03.cloudmatchbeta.nvidiagrid.net/", printedWasteZoneUrl("NP-LAX-03"))
    }

    @Test
    fun serversSharingALocationCollapseIntoOneRow() {
        val mapping = mapOf(
            "NP-LAX-02" to entry("Southern California", "US Southwest", gpu4080 = true),
            "NP-LAX-03" to entry("Southern California", "US Southwest", gpu5080 = true),
            "NP-SJC6-04" to entry("Northern California", "US West"),
        )
        val zones = listOf(
            zone("NP-LAX-02", queue = 40, ping = 60),
            zone("NP-LAX-03", queue = 2, ping = 22),
            zone("NP-SJC6-04", queue = 1, ping = 90),
        )

        val locations = printedWasteLocations(zones, mapping)

        assertEquals(2, locations.size)
        val socal = locations.first { it.title == "Southern California" }
        // The row routes to the better of the two, not to whichever id sorted first.
        assertEquals("NP-LAX-03", socal.primary.zoneId)
        assertEquals(1, socal.alternateCount)
        assertEquals("US Southwest", socal.region)
        // The best GPU anywhere in the group is what the location can offer.
        assertEquals(PrintedWasteGpuTier.Rtx5080, socal.gpuTier)
    }

    @Test
    fun anUnmappedZoneStaysSelectableUnderItsRawId() {
        val zones = listOf(zone("NP-XYZ-01", queue = 3, ping = 30, region = "EU"))

        val locations = printedWasteLocations(zones, emptyMap())

        assertEquals("NP-XYZ-01", locations.single().title)
        // Falls back to the queue payload's continent code rather than showing nothing.
        assertEquals("Europe", locations.single().region)
        assertNull(locations.single().gpuTier)
    }

    @Test
    fun regionsAreOrderedByTheirStrongestLocation() {
        val mapping = mapOf(
            "NP-LAX-03" to entry("Southern California", "US Southwest"),
            "NP-ASH-04" to entry("Virginia", "US East"),
            "NP-NWK-04" to entry("New Jersey", "US Northeast"),
        )
        val zones = listOf(
            zone("NP-ASH-04", queue = 2, ping = 140),
            zone("NP-LAX-03", queue = 1, ping = 18),
            zone("NP-NWK-04", queue = 2, ping = 80),
        )
        val maxPing = 140L
        val maxQueue = 2

        val groups = printedWasteRegionGroups(printedWasteLocations(zones, mapping), maxPing, maxQueue)

        assertEquals(listOf("US Southwest", "US Northeast", "US East"), groups.map { it.first })
    }

    @Test
    fun theRecommendationPrefersLowPingOverAShorterQueue() {
        val zones = listOf(
            zone("NP-LAX-03", queue = 12, ping = 20),
            zone("NP-ASH-04", queue = 1, ping = 180),
        )

        assertEquals("NP-LAX-03", recommendedPrintedWasteZone(zones)?.zoneId)
    }

    @Test
    fun waitTimesReadAsMinutesThenHours() {
        assertEquals("1m", formatPrintedWasteWait(1_000L))
        assertEquals("5m", formatPrintedWasteWait(5L * 60_000L))
        assertEquals("1h 30m", formatPrintedWasteWait(90L * 60_000L))
    }
}
