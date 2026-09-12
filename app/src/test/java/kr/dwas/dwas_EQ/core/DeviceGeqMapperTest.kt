package kr.dwas.dwas_EQ.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGeqMapperTest {
    @Test fun mapsTenDeviceAnchorsToTwentyDolbyValues() {
        val input = floatArrayOf(-6f,-5f,-4f,-3f,-2f,-1f,0f,1f,2f,3f)
        val mapped = DeviceGeqMapper.uiDbToDap(input)
        assertEquals(20, mapped.size)
        assertEquals(-96, mapped.first())
        assertTrue(mapped.toList().zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun halfDbDeviceAnchorInfluencesProjectedDapCurve() {
        val input = FloatArray(10).also { it[1] = 0.5f }
        val mapped = DeviceGeqMapper.uiDbToDap(input)
        assertTrue(mapped.any { it > 0 })
    }

    @Test fun clampsAndQuantizesToHalfDb() {
        assertEquals(0.5f, DeviceGeqMapper.quantizeDb(0.26f))
        assertEquals(-6f, DeviceGeqMapper.quantizeDb(-9f))
        assertEquals(6f, DeviceGeqMapper.quantizeDb(9f))
    }
}
