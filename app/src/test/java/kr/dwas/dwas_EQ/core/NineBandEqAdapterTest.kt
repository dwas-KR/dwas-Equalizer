package kr.dwas.dwas_EQ.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NineBandEqAdapterTest {
    @Test fun mapsNineEditorBandsToTwentyDolbyValues() {
        val input = floatArrayOf(-6f,-5f,-4f,-3f,-2f,-1f,0f,1f,2f)
        val mapped = NineBandEqAdapter.uiDbToDap(input)
        assertEquals(20, mapped.size)
        assertEquals(-96, mapped.first())
        assertTrue(mapped.toList().zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun flatNineBandCurveMapsToFlatDolbyCurve() {
        val mapped = NineBandEqAdapter.uiDbToDap(FloatArray(9))
        assertTrue(mapped.contentEquals(IntArray(20)))
    }
}
