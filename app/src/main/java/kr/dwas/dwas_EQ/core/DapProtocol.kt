package kr.dwas.dwas_EQ.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

object DapProtocol {
    fun intToBytes(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    fun intsToBytes(values: IntArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putInt)
        return buffer.array()
    }

    fun bytesToInts(bytes: ByteArray): IntArray {
        require(bytes.size % 4 == 0) { "DAP byte array must be aligned to int32" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(bytes.size / 4) { buffer.int }
    }

    fun buildWritePayload(profile: Int, parameter: Int, gains: IntArray): ByteArray {
        require(gains.size == EqSpec.DAP_GAIN_COUNT) { "GEQ parameter requires 20 gains" }
        return intsToBytes(intArrayOf(EqSpec.DAP_WRITE_MARKER, 21, profile, parameter) + gains)
    }

    fun buildScalarWritePayload(profile: Int, parameter: Int, value: Int): ByteArray =
        intsToBytes(intArrayOf(EqSpec.DAP_WRITE_MARKER, 2, profile, parameter, value))

    fun buildReadKeyInt(profile: Int, parameter: Int): Int =
        EqSpec.DAP_READ_MARKER + (profile shl 8) + (parameter shl 16)

    fun buildReadKey(profile: Int, parameter: Int): ByteArray = intToBytes(buildReadKeyInt(profile, parameter))

    fun decodeGains(bytes: ByteArray): IntArray {
        require(bytes.size >= EqSpec.DAP_GAIN_COUNT * 4) { "Expected at least 80 bytes for 20 DAP gains" }
        return bytesToInts(bytes.copyOf(EqSpec.DAP_GAIN_COUNT * 4))
    }

    fun decodeScalar(bytes: ByteArray): Int {
        require(bytes.size >= 4) { "Expected at least 4 bytes for scalar DAP value" }
        return bytesToInts(bytes.copyOf(4)).first()
    }
}
