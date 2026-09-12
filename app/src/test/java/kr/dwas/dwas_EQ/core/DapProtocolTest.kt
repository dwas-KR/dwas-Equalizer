package kr.dwas.dwas_EQ.core

import org.junit.Assert.assertEquals
import org.junit.Test

class DapProtocolTest {
    @Test fun encodesMusicGeqWritePacket() {
        val packet = DapProtocol.bytesToInts(DapProtocol.buildWritePayload(2, 110, IntArray(20) { it }))
        assertEquals(24, packet.size)
        assertEquals(0x01000000, packet[0])
        assertEquals(21, packet[1])
        assertEquals(2, packet[2])
        assertEquals(110, packet[3])
        assertEquals(19, packet[23])
    }

    @Test fun encodesMusicGeqEnablePacket() {
        val packet = DapProtocol.bytesToInts(DapProtocol.buildScalarWritePayload(2, 106, 1))
        assertEquals(5, packet.size)
        assertEquals(0x01000000, packet[0])
        assertEquals(2, packet[1])
        assertEquals(2, packet[2])
        assertEquals(106, packet[3])
        assertEquals(1, packet[4])
    }

    @Test fun buildsObservedReadKey() {
        assertEquals(0x016e0205, DapProtocol.buildReadKeyInt(2, 110))
        assertEquals(0x016a0205, DapProtocol.buildReadKeyInt(2, 106))
    }
}
