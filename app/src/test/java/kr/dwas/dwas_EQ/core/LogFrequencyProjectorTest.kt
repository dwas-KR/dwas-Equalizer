package kr.dwas.dwas_EQ.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LogFrequencyProjectorTest {
    @Test fun interpolatesOnLogFrequencyAxisAndClampsEdges() {
        val out = LogFrequencyProjector.interpolate(
            intArrayOf(100, 1000),
            floatArrayOf(0f, 10f),
            intArrayOf(50, 100, 316, 1000, 2000),
        )
        assertEquals(0f, out[0], 0.01f)
        assertEquals(0f, out[1], 0.01f)
        assertEquals(5f, out[2], 0.03f)
        assertEquals(10f, out[3], 0.01f)
        assertEquals(10f, out[4], 0.01f)
    }
}
