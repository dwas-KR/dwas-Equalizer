package kr.dwas.dwas_EQ.device

import kr.dwas.dwas_EQ.standardfx.AudioEffectRoute
import kr.dwas.dwas_EQ.standardfx.AudioEffectSafety
import kr.dwas.dwas_EQ.standardfx.EffectRouteResolver
import kr.dwas.dwas_EQ.standardfx.StandardFxTopologyEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB361FUCompatibilityPolicyTest {
    @Test fun capturedGlobalRomUsesStaticGlobalDynamicsStrengthFallbackCandidate() {
        val device = DeviceCompatibilityDatabase.find("TB361FU")!!
        assertEquals("cava", device.lgsiProject)
        assertEquals(15, device.androidVersion)
        assertEquals("mt6835", device.platform)
        assertEquals("mt8755", device.hardware)
        assertEquals(SocFamily.MEDIA_TEK, device.socFamily)
        assertEquals(15, device.daxGeneration)
        assertTrue(device.tuningCaptured)
        assertFalse(device.capabilityOnly)
        assertFalse(device.nativeFxVerified)
        assertFalse(device.fullSessionStandardFx)
        assertFalse(device.noDynamicsProcessingStandardFx)
        assertFalse(device.speakerSafeFourFx)
        assertFalse(device.dolbyGeqRuntimeInactive)
        assertTrue(device.publicBassBoostRuntimeInactive)
        assertTrue(device.publicVirtualizerRuntimeInactive)

        val profile = EffectRouteResolver.resolve(
            StandardFxTopologyEvidence(
                qualcomm = false,
                qtiBassProxy = false,
                qtiVirtualizerProxy = false,
                directSoftwareBass = true,
                directSoftwareVirtualizer = true,
                directSoftwareDynamics = true,
                knownFullSessionQuirk = device.fullSessionStandardFx,
                knownNoDynamicsProcessingQuirk = device.noDynamicsProcessingStandardFx,
                knownNativeFxVerified = device.nativeFxVerified,
                knownSpeakerSafeFourFxQuirk = device.speakerSafeFourFx,
                knownPublicBassBoostRuntimeInactive = device.publicBassBoostRuntimeInactive,
                knownPublicVirtualizerRuntimeInactive = device.publicVirtualizerRuntimeInactive,
            ),
            presetReverbDescriptorPresent = true,
        )
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.attenuator.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.balance.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.limiter.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.bass.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.virtualizer.route)
        assertEquals(AudioEffectSafety.STATIC_SUPPORTED, profile.bass.safety)
        assertEquals(AudioEffectSafety.STATIC_SUPPORTED, profile.virtualizer.safety)
    }
}
