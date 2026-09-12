package kr.dwas.dwas_EQ.standardfx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB373FUAudioContinuityPolicyTest {
    @Test fun bassOverlayAcceptsOnlySmallNativeQuantizationDifference() {
        assertTrue(SessionFxStabilityPolicy.bassOverlayNativeReadbackMatches(1198, 1200))
        assertTrue(SessionFxStabilityPolicy.bassOverlayNativeReadbackMatches(149, 100))
        assertTrue(SessionFxStabilityPolicy.bassOverlayNativeReadbackMatches(75, 100))
        assertFalse(SessionFxStabilityPolicy.bassOverlayNativeReadbackMatches(1200, 1000))
        assertFalse(SessionFxStabilityPolicy.bassOverlayNativeReadbackMatches(600, 0))
    }

    @Test fun newlyOpenedSpeakerVirtualizerWarmsBeforeNonzeroActivation() {
        assertTrue(SessionFxStabilityPolicy.shouldWarmNewSpeakerVirtualizer(true, true, 1))
        assertTrue(SessionFxStabilityPolicy.shouldWarmNewSpeakerVirtualizer(true, true, 1000))
        assertFalse(SessionFxStabilityPolicy.shouldWarmNewSpeakerVirtualizer(false, true, 1000))
        assertFalse(SessionFxStabilityPolicy.shouldWarmNewSpeakerVirtualizer(true, false, 1000))
        assertFalse(SessionFxStabilityPolicy.shouldWarmNewSpeakerVirtualizer(true, true, 0))
    }

    @Test fun eqCurveOnlyRequiresStandardFxReapplyForAutomaticAttenuation() {
        assertFalse(SessionFxStabilityPolicy.curveRequiresStandardFxReapply(false, false))
        assertFalse(SessionFxStabilityPolicy.curveRequiresStandardFxReapply(true, false))
        assertFalse(SessionFxStabilityPolicy.curveRequiresStandardFxReapply(false, true))
        assertTrue(SessionFxStabilityPolicy.curveRequiresStandardFxReapply(true, true))
    }
}
