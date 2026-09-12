package kr.dwas.dwas_EQ.standardfx

import kr.dwas.dwas_EQ.core.EqSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB373FUEqRangePolicyTest {
    @Test fun scalarDolbyReadbackUsesVendorCompatibleMinimumBuffer() {
        assertEquals(12, EqSpec.DAP_SCALAR_READBACK_BYTES)
    }

    @Test fun equalizerReadbackAcceptsOnlyNativeHalfStepQuantization() {
        assertTrue(SessionFxStabilityPolicy.equalizerNativeReadbackMatches(349, 300))
        assertTrue(SessionFxStabilityPolicy.equalizerNativeReadbackMatches(-349, -300))
        assertTrue(SessionFxStabilityPolicy.equalizerNativeReadbackMatches(650, 600))
        assertFalse(SessionFxStabilityPolicy.equalizerNativeReadbackMatches(651, 600))
        assertFalse(SessionFxStabilityPolicy.equalizerNativeReadbackMatches(500, 400))
    }
}
