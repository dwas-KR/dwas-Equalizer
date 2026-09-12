package kr.dwas.dwas_EQ.standardfx

import kr.dwas.dwas_EQ.domain.EqCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardFxSettingsTest {
    @Test fun stagedEqSwitchDoesNotChangeHeadroomUntilAppliedTransactionChanges() {
        val lastApplied = EqCurve.of(listOf(5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
        assertEquals(lastApplied, HeadroomCalculator.appliedCurve(true, lastApplied))
        assertEquals(EqCurve.flat(), HeadroomCalculator.appliedCurve(false, lastApplied))
    }

    @Test fun automaticAttenuationUsesLargestPositiveEqBoost() {
        val curve = EqCurve.of(listOf(0f, 1f, 2.5f, 4f, -6f, 0f, 1f, 0f, 0f))
        val settings = StandardFxSettings(attenuatorEnabled = true, automaticAttenuation = true)
        assertEquals(-4f, HeadroomCalculator.attenuationDb(settings, curve), 0f)
    }

    @Test fun channelBalanceAddsToSharedAttenuation() {
        val settings = StandardFxSettings(
            channelBalanceEnabled = true,
            leftBalanceDb = -1.2f,
            rightBalanceDb = -3.4f,
        )
        val (left, right) = HeadroomCalculator.channelInputGainsDb(settings, -2f)
        assertEquals(-3.2f, left, 0.001f)
        assertEquals(-5.4f, right, 0.001f)
    }

    @Test fun reverbNoneDoesNotKeepRuntimeAlive() {
        assertFalse(StandardFxSettings(reverbEnabled = true, reverbPreset = ReverbPreset.NONE).hasAnyEnabledEffect)
        assertTrue(StandardFxSettings(reverbEnabled = true, reverbPreset = ReverbPreset.PLATE).hasAnyEnabledEffect)
    }

    @Test fun limiterDefaultsMatchApprovedDesign() {
        val limiter = LimiterSettings()
        assertEquals(1f, limiter.attackMs, 0f)
        assertEquals(60f, limiter.releaseMs, 0f)
        assertEquals(10f, limiter.ratio, 0f)
        assertEquals(-2f, limiter.thresholdDb, 0f)
        assertEquals(0f, limiter.postGainDb, 0f)
    }
    @Test fun enablingBassBoostPreservesZeroAndExistingStrength() {
        val enabledFromZero = StandardFxSettings().withBassBoostEnabled(true)
        assertTrue(enabledFromZero.bassBoostEnabled)
        assertEquals(0, enabledFromZero.bassBoostStrength)
        assertFalse(enabledFromZero.hasAnyEnabledEffect)
        assertTrue(enabledFromZero.requiresRuntimeHold)
        assertEquals(640, StandardFxSettings(bassBoostStrength = 640).withBassBoostEnabled(true).bassBoostStrength)
        assertEquals(640, StandardFxSettings(bassBoostEnabled = true, bassBoostStrength = 640).withBassBoostEnabled(false).bassBoostStrength)
    }

    @Test fun enablingVirtualizerPreservesZeroAndExistingStrength() {
        val enabledFromZero = StandardFxSettings().withVirtualizerEnabled(true)
        assertTrue(enabledFromZero.virtualizerEnabled)
        assertEquals(0, enabledFromZero.virtualizerStrength)
        assertFalse(enabledFromZero.hasAnyEnabledEffect)
        assertTrue(enabledFromZero.requiresRuntimeHold)
        assertEquals(420, StandardFxSettings(virtualizerStrength = 420).withVirtualizerEnabled(true).virtualizerStrength)
        assertEquals(420, StandardFxSettings(virtualizerEnabled = true, virtualizerStrength = 420).withVirtualizerEnabled(false).virtualizerStrength)
    }

    @Test fun enablingReverbAlwaysStartsAtNoneAndDisablingClearsPreset() {
        val enabledFromNone = StandardFxSettings().withReverbEnabled(true)
        assertTrue(enabledFromNone.reverbEnabled)
        assertEquals(ReverbPreset.NONE, enabledFromNone.reverbPreset)
        assertEquals(ReverbPreset.NONE, StandardFxSettings(reverbPreset = ReverbPreset.PLATE).withReverbEnabled(true).reverbPreset)
        assertEquals(ReverbPreset.NONE, StandardFxSettings(reverbEnabled = true, reverbPreset = ReverbPreset.PLATE).withReverbEnabled(false).reverbPreset)
    }

}
