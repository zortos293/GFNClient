package com.opencloudgaming.opennow

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class InputEncoderMouseTest {
    @Test fun coalescedRelativeMotionCrossingTheWireLimitIsSplitWithoutLoss() {
        val encoder = InputEncoder().apply { setProtocolVersion(2) }
        val packets = encoder.encodeMouseMoves(80000, -70000).toList()
        assertEquals(3, packets.size)
        assertTrue(packets.all { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).int == 7 })
        assertEquals(80000, packets.sumOf { ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).getShort(4).toInt() })
        assertEquals(-70000, packets.sumOf { ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).getShort(6).toInt() })
    }

    @Test fun zeroMotionProducesNoPacket() {
        assertFalse(InputEncoder().encodeMouseMoves(0, 0).iterator().hasNext())
    }
}
