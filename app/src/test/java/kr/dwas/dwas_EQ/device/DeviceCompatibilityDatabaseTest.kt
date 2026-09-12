package kr.dwas.dwas_EQ.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilityDatabaseTest {
    @Test fun containsAllAnalyzedModels() {
        val expectedModels = listOf("TB320FC","TB322FC","TB323FU","TB331FC","TB335FC","TB336FU","TB361FU","TB365FC","TB373FU","TB375FC","TB376FC","TB378FC","TB371FC","TB390FU","TB520FU","TB522FU","TB710FU")
        assertEquals(expectedModels.size, DeviceCompatibilityDatabase.models.size)
        for (model in expectedModels) {
            assertTrue(DeviceCompatibilityDatabase.find(model) != null)
        }
    }

    @Test fun tb336UsesCapturedStereoMediaTekStrengthFxFallbackValidated() {
        val tb336 = DeviceCompatibilityDatabase.find("TB336FU")!!
        assertEquals("sycamore", tb336.lgsiProject)
        assertEquals(16, tb336.androidVersion)
        assertEquals("mt6835", tb336.platform)
        assertEquals("mt8755", tb336.hardware)
        assertEquals(16, tb336.daxGeneration)
        assertTrue(tb336.tuningCaptured)
        assertFalse(tb336.capabilityOnly)
        assertTrue(tb336.publicBassBoostRuntimeInactive)
        assertTrue(tb336.publicVirtualizerRuntimeInactive)
        assertTrue(tb336.nativeFxVerified)
    }

    @Test fun tb361UsesCapturedStereoMediaTekStrengthFxFallbackCandidate() {
        val tb361 = DeviceCompatibilityDatabase.find("TB361FU")!!
        assertEquals("cava", tb361.lgsiProject)
        assertEquals(15, tb361.androidVersion)
        assertEquals("mt6835", tb361.platform)
        assertEquals("mt8755", tb361.hardware)
        assertEquals(15, tb361.daxGeneration)
        assertTrue(tb361.tuningCaptured)
        assertFalse(tb361.capabilityOnly)
        assertTrue(tb361.publicBassBoostRuntimeInactive)
        assertTrue(tb361.publicVirtualizerRuntimeInactive)
        assertFalse(tb361.nativeFxVerified)
        assertFalse(tb361.fullSessionStandardFx)
        assertFalse(tb361.noDynamicsProcessingStandardFx)
    }

    @Test fun tb365UsesCapturedStereoMediaTekStrengthFxFallbackValidated() {
        val tb365 = DeviceCompatibilityDatabase.find("TB365FC")!!
        assertEquals("cava", tb365.lgsiProject)
        assertEquals(16, tb365.androidVersion)
        assertEquals("mt6835", tb365.platform)
        assertEquals("mt8755", tb365.hardware)
        assertEquals(16, tb365.daxGeneration)
        assertTrue(tb365.tuningCaptured)
        assertFalse(tb365.capabilityOnly)
        assertTrue(tb365.publicBassBoostRuntimeInactive)
        assertTrue(tb365.publicVirtualizerRuntimeInactive)
        assertTrue(tb365.nativeFxVerified)
        assertFalse(tb365.fullSessionStandardFx)
        assertFalse(tb365.noDynamicsProcessingStandardFx)
    }

    @Test fun tb373UsesSpeakerSafeFxAndAndroidEqAutoFallback() {
        val tb373 = DeviceCompatibilityDatabase.find("TB373FU")!!
        assertTrue(tb373.fullSessionStandardFx)
        assertTrue(tb373.noDynamicsProcessingStandardFx)
        assertTrue(tb373.speakerSafeFourFx)
        assertTrue(tb373.dolbyGeqRuntimeInactive)
    }

    @Test fun tb376UsesPlaybackSessionStandardFx() {
        val tb376 = DeviceCompatibilityDatabase.find("TB376FC")!!
        assertTrue(tb376.fullSessionStandardFx)
    }

    @Test fun tb378UsesPlaybackSessionStandardFxCandidate() {
        val tb378 = DeviceCompatibilityDatabase.find("TB378FC")!!
        assertTrue(tb378.fullSessionStandardFx)
    }

    @Test fun tb375UsesSpeakerSafeMediaTekRoutingCandidate() {
        val tb375 = DeviceCompatibilityDatabase.find("TB375FC")!!
        assertTrue(tb375.fullSessionStandardFx)
        assertTrue(tb375.noDynamicsProcessingStandardFx)
        assertTrue(tb375.speakerSafeFourFx)
        assertFalse(tb375.nativeFxVerified)
    }

    @Test fun tb520NativeFxIsRealDeviceVerified() {
        val tb520 = DeviceCompatibilityDatabase.find("TB520FU")!!
        assertTrue(tb520.nativeFxVerified)
    }

    @Test fun tb522RemainsCapabilityOnly() {
        val tb522 = DeviceCompatibilityDatabase.find("TB522FU")!!
        assertFalse(tb522.tuningCaptured)
        assertTrue(tb522.capabilityOnly)
    }
}
