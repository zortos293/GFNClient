package com.opencloudgaming.opennow

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalMouseAbsolutePositionTest {
    @Test
    fun capturedDeltasMoveAndClampInsideTheRemoteExtent() {
        val position = ExternalMouseAbsolutePosition()

        assertEquals(AbsoluteMousePosition(1060, 520, 1920, 1080), position.moveBy(100, -20, 1920, 1080))
        assertEquals(AbsoluteMousePosition(1920, 0, 1920, 1080), position.moveBy(5000, -5000, 1920, 1080))

        position.reset()
        assertEquals(AbsoluteMousePosition(960, 540, 1920, 1080), position.moveBy(0, 0, 1920, 1080))
    }

    @Test
    fun absolutePacketMatchesDesktopTypeFiveLayout() {
        val encoder = InputEncoder().also { it.setProtocolVersion(2) }
        val packet = encoder.encodeMouseAbsolute(1060, 520, 1920, 1080)
        val littleEndian = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val bigEndian = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        assertEquals(26, packet.size)
        assertEquals(InputEncoder.INPUT_MOUSE_ABS, littleEndian.getInt(0))
        assertEquals(1060, bigEndian.getShort(4).toInt() and 0xffff)
        assertEquals(520, bigEndian.getShort(6).toInt() and 0xffff)
        assertEquals(1920, bigEndian.getShort(10).toInt() and 0xffff)
        assertEquals(1080, bigEndian.getShort(12).toInt() and 0xffff)
    }
}
