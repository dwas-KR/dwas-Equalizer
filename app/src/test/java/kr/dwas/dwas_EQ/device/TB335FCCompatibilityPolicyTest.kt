package kr.dwas.dwas_EQ.device

import kr.dwas.dwas_EQ.domain.EqCurve
import kr.dwas.dwas_EQ.standardfx.AudioEffectRoute
import kr.dwas.dwas_EQ.standardfx.AudioEffectSafety
import kr.dwas.dwas_EQ.standardfx.EffectRouteResolver
import kr.dwas.dwas_EQ.standardfx.StandardFxCompatibilityPolicy
import kr.dwas.dwas_EQ.standardfx.StandardFxRoutingPolicy
import kr.dwas.dwas_EQ.standardfx.StandardFxSettings
import kr.dwas.dwas_EQ.standardfx.StandardFxTopologyEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB335FCCompatibilityPolicyTest {
    @Test
    fun realDeviceFalsePositiveStrengthEffectsUseOnlyDwasDynamicsFallback() {
        val device = DeviceCompatibilityDatabase.find("TB335FC")!!
        assertEquals(16, device.androidVersion)
        assertEquals("mt6835", device.platform)
        assertEquals("mt8755", device.hardware)
        assertEquals(16, device.daxGeneration)
        assertFalse(device.fullSessionStandardFx)
        assertFalse(device.noDynamicsProcessingStandardFx)
        assertFalse(device.speakerSafeFourFx)
        assertTrue(device.nativeFxVerified)
        assertFalse(device.spatializerEvidence)
        assertFalse(device.dolbyGeqRuntimeInactive)
        assertTrue(device.publicBassBoostRuntimeInactive)
        assertTrue(device.publicVirtualizerRuntimeInactive)
        assertTrue(device.note.contains("stereo 0x3"))
        assertTrue(device.note.contains("false-positive"))
        assertTrue(device.note.contains("validated on real hardware"))

        val evidence = StandardFxTopologyEvidence(
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
        )
        assertFalse(StandardFxCompatibilityPolicy.routeFullStandardFxToPlaybackSession(evidence))
        assertFalse(StandardFxCompatibilityPolicy.routeSessionStrengthFxWithoutDynamics(evidence))
        val profile = EffectRouteResolver.resolve(evidence, presetReverbDescriptorPresent = true)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.attenuator.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.balance.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.limiter.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.bass.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.virtualizer.route)
        assertEquals(AudioEffectRoute.SESSION_INSERT_REVERB, profile.reverb.route)
        assertEquals(AudioEffectSafety.VERIFIED_SAFE, profile.bass.safety)
        assertEquals(AudioEffectSafety.VERIFIED_SAFE, profile.virtualizer.safety)

        val routed = StandardFxRoutingPolicy.routeByProfile(
            requested = StandardFxSettings(
                bassBoostEnabled = true,
                bassBoostStrength = 700,
                virtualizerEnabled = true,
                virtualizerStrength = 650,
            ),
            profile = profile,
            lastAppliedCurve = EqCurve.flat(),
            globalFailedEffects = emptySet(),
        )
        assertTrue(routed.globalNativeBass)
        assertTrue(routed.globalNativeVirtualizer)
        assertTrue(routed.global.bassBoostEnabled)
        assertTrue(routed.global.virtualizerEnabled)
        assertFalse(routed.session.requiresRuntimeHold)
    }
}
