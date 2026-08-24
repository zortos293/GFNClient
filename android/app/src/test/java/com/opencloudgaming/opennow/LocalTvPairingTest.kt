package com.opencloudgaming.opennow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTvPairingTest {
    @Test
    fun pairingCodeRequiresExactlyFourDigits() {
        assertEquals("0427", normalizeLocalTvPairingCode(" 0427 "))
        assertNull(normalizeLocalTvPairingCode("427"))
        assertNull(normalizeLocalTvPairingCode("04270"))
        assertNull(normalizeLocalTvPairingCode("04A7"))
    }
}
