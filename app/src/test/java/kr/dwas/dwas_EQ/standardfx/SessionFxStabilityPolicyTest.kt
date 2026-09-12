package kr.dwas.dwas_EQ.standardfx

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFxStabilityPolicyTest {
    @Test fun verificationUsesShortSettlingWindow() {
        assertArrayEquals(longArrayOf(0L, 20L, 40L, 80L), SessionFxStabilityPolicy.verificationRetryDelaysMs)
    }

    @Test fun environmentalReverbKeepsCriticalReadbackExact() {
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(0, -700, -700))
        assertFalse(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(0, -700, -699))
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(3, 780, 780))
        assertFalse(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(3, 780, 781))
    }

    @Test fun environmentalReverbAcceptsImplementationNormalizedTailWithinSafeRange() {
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(4, -1000, -900))
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(5, 22, 24))
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(6, -300, -250))
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(7, 36, 40))
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(8, 920, 900))
        assertTrue(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(9, 930, 900))
        assertFalse(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(4, -1000, 1001))
        assertFalse(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(9, 930, 1001))
    }
}
