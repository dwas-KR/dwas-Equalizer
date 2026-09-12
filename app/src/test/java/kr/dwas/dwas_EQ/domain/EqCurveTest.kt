package kr.dwas.dwas_EQ.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EqCurveTest {
    @Test fun clampsAndQuantizesBandsToHalfDb() {
        val curve = EqCurve.of(listOf(6.4f, -9f, 0.26f) + List(6) { 0f })
        assertEquals(6f, curve.gainsDb[0], 0f)
        assertEquals(-6f, curve.gainsDb[1], 0f)
        assertEquals(0.5f, curve.gainsDb[2], 0f)
    }

    @Test fun editingReturnsIndependentImmutableValue() {
        val flat = EqCurve.flat()
        val edited = flat.withBand(3, 2.2f)
        assertEquals(0f, flat.gainsDb[3], 0f)
        assertEquals(2f, edited.gainsDb[3], 0f)
        assertFalse(flat == edited)
    }

    @Test fun formatsHzAndKhzWithoutMixedKHzSuffix() {
        assertEquals("234 Hz", EqCurve.formatFrequency(234))
        assertEquals("1 kHz", EqCurve.formatFrequency(1000))
        assertEquals("13 kHz", EqCurve.formatFrequency(13000))
    }
}
