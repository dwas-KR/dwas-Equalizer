package kr.dwas.dwas_EQ.device

import kr.dwas.dwas_EQ.standardfx.AudioEffectRoute
import kr.dwas.dwas_EQ.standardfx.AudioEffectSafety
import kr.dwas.dwas_EQ.standardfx.EffectRouteResolver
import kr.dwas.dwas_EQ.standardfx.StandardFxCompatibilityPolicy
import kr.dwas.dwas_EQ.standardfx.StandardFxTopologyEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB323FUCompatibilityPolicyTest {
    @Test
    fun capturedStereoTopologyKeepsGlobalCandidateWithoutPrematureVerification() {
        val device = DeviceCompatibilityDatabase.find("TB323FU")!!
        assertEquals(16, device.androidVersion)
        assertEquals("canoe", device.platform)
        assertEquals("qcom", device.hardware)
        assertFalse(device.fullSessionStandardFx)
        assertTrue(device.nativeFxVerified)
        assertFalse(device.spatializerEvidence)
        assertTrue(device.note.contains("TB322FC"))
        assertTrue(device.note.contains("validated on real hardware"))

        val evidence = StandardFxTopologyEvidence(
            qualcomm = true,
            qtiBassProxy = false,
            qtiVirtualizerProxy = false,
            directSoftwareBass = true,
            directSoftwareVirtualizer = true,
            directSoftwareDynamics = true,
            knownFullSessionQuirk = device.fullSessionStandardFx,
            knownNativeFxVerified = device.nativeFxVerified,
        )
        val profile = EffectRouteResolver.resolve(evidence, presetReverbDescriptorPresent = true)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.attenuator.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.balance.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.limiter.route)
        assertEquals(AudioEffectRoute.GLOBAL_PUBLIC_BASS, profile.bass.route)
        assertEquals(AudioEffectRoute.GLOBAL_PUBLIC_VIRTUALIZER, profile.virtualizer.route)
        assertEquals(AudioEffectRoute.SESSION_INSERT_REVERB, profile.reverb.route)
        assertEquals(AudioEffectSafety.STATIC_SUPPORTED, profile.bass.safety)
        assertEquals(AudioEffectSafety.STATIC_SUPPORTED, profile.virtualizer.safety)
        assertTrue(StandardFxCompatibilityPolicy.routeFullStandardFxToPlaybackSession(evidence))
    }
}
