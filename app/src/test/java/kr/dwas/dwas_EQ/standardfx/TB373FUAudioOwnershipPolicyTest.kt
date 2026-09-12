package kr.dwas.dwas_EQ.standardfx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB373FUAudioOwnershipPolicyTest {
    @Test fun bassOverlayUsesExactNativeBaselineWhenNoEqBaseExists() {
        assertEquals(
            1200.toShort(),
            SpeakerSafeBassOverlayPolicy.overlayMilliBelsFromBaseline(
                centerHz = 63,
                baselineMilliBels = 0,
                strength = 1000,
                minMilliBels = -1500,
                maxMilliBels = 1500,
            ),
        )
        assertEquals(
            250.toShort(),
            SpeakerSafeBassOverlayPolicy.overlayMilliBelsFromBaseline(
                centerHz = 1000,
                baselineMilliBels = 250,
                strength = 1000,
                minMilliBels = -1500,
                maxMilliBels = 1500,
            ),
        )
    }

    @Test fun bassOverlayClampsAgainstNativeBandRange() {
        assertEquals(
            1500.toShort(),
            SpeakerSafeBassOverlayPolicy.overlayMilliBelsFromBaseline(
                centerHz = 63,
                baselineMilliBels = 1000,
                strength = 1000,
                minMilliBels = -1500,
                maxMilliBels = 1500,
            ),
        )
    }

    @Test fun speakerVirtualizerModeIsForcedOnlyUntilPrimed() {
        assertTrue(SessionFxStabilityPolicy.shouldForceSpeakerVirtualizationMode(true, 1, false))
        assertFalse(SessionFxStabilityPolicy.shouldForceSpeakerVirtualizationMode(true, 1, true))
        assertFalse(SessionFxStabilityPolicy.shouldForceSpeakerVirtualizationMode(true, 0, false))
        assertFalse(SessionFxStabilityPolicy.shouldForceSpeakerVirtualizationMode(false, 1000, false))
    }

    @Test fun sameHealthyEnvironmentalReverbCanBeReused() {
        assertTrue(
            SessionFxStabilityPolicy.shouldReuseEnvironmentalReverb(
                hasControl = true,
                enabled = true,
                activePreset = ReverbPreset.LARGE_HALL,
                requestedPreset = ReverbPreset.LARGE_HALL,
            ),
        )
        assertFalse(
            SessionFxStabilityPolicy.shouldReuseEnvironmentalReverb(
                hasControl = true,
                enabled = true,
                activePreset = ReverbPreset.LARGE_HALL,
                requestedPreset = ReverbPreset.PLATE,
            ),
        )
        assertFalse(
            SessionFxStabilityPolicy.shouldReuseEnvironmentalReverb(
                hasControl = true,
                enabled = false,
                activePreset = ReverbPreset.LARGE_HALL,
                requestedPreset = ReverbPreset.LARGE_HALL,
            ),
        )
    }
}
