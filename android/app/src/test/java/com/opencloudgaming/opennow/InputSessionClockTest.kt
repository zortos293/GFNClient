package com.opencloudgaming.opennow

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputSessionClockTest {
    @Test
    fun timestampsAreRelativeToTheInputHandshake() {
        var nowNanos = 8_000_000_000L
        val clock = InputSessionClock { nowNanos }

        clock.start()
        nowNanos += 1_750_000L

        assertEquals(1_750L, clock.timestampUs())
    }

    @Test
    fun protocolV3OuterTimestampIsRestampedAtSendTime() {
        val packet = ByteArray(34).also { it[0] = 0x23 }

        assertTrue(restampProtocolV3OuterTimestamp(packet, nowUs = 4_242L))
        assertEquals(
            4_242L,
            ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN).getLong(1),
        )
    }

    @Test
    fun rawProtocolV2PacketsAreNotModified() {
        val packet = byteArrayOf(2, 0, 0, 0)

        assertFalse(restampProtocolV3OuterTimestamp(packet, nowUs = 4_242L))
        assertEquals(listOf<Byte>(2, 0, 0, 0), packet.toList())
    }
}
