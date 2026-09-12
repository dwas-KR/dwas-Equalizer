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

class TB710FUCompatibilityPolicyTest {
    @Test fun keepsCapturedPineappleGlobalCandidateWithoutEnablingSpatializerRouting() {
        val device = DeviceCompatibilityDatabase.find("TB710FU")!!
        assertEquals("topaz", device.lgsiProject)
        assertEquals("pineapple", device.platform)
        assertEquals("qcom", device.hardware)
        assertEquals(16, device.androidVersion)
        assertTrue(device.spatializerEvidence)
        assertFalse(device.fullSessionStandardFx)
        assertTrue(device.nativeFxVerified)
        assertFalse(device.dolbyGeqRuntimeInactive)
        assertTrue(device.note.contains("stereo 0x3", ignoreCase = true))
        assertTrue(device.note.contains("TB520FU", ignoreCase = true))
        assertTrue(device.note.contains("Spatializer", ignoreCase = true))
        assertTrue(device.note.contains("disabled", ignoreCase = true))
        assertTrue(device.note.contains("validated on real hardware", ignoreCase = true))
    }

    @Test fun keepsTb710ProxyAwareGlobalRoutesStaticUntilRealDeviceValidation() {
        val device = DeviceCompatibilityDatabase.find("TB710FU")!!
        val evidence = StandardFxTopologyEvidence(
            qualcomm = true,
            qtiBassProxy = true,
            qtiVirtualizerProxy = true,
            directSoftwareBass = true,
            directSoftwareVirtualizer = true,
            directSoftwareDynamics = true,
            knownFullSessionQuirk = device.fullSessionStandardFx,
            knownNativeFxVerified = device.nativeFxVerified,
        )
        assertFalse(StandardFxCompatibilityPolicy.routeFullStandardFxToPlaybackSession(evidence))
        val profile = EffectRouteResolver.resolve(evidence, presetReverbDescriptorPresent = true)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.attenuator.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.balance.route)
        assertEquals(AudioEffectRoute.GLOBAL_DYNAMICS, profile.limiter.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.bass.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.virtualizer.route)
        assertEquals(AudioEffectRoute.SESSION_INSERT_REVERB, profile.reverb.route)
        assertEquals(AudioEffectSafety.VERIFIED_SAFE, profile.bass.safety)
        assertEquals(AudioEffectSafety.VERIFIED_SAFE, profile.virtualizer.safety)
    }

    @Test fun recordsTb375RequiredGateAsRealDeviceValidated() {
        val tb375 = DeviceCompatibilityDatabase.find("TB375FC")!!
        assertTrue(tb375.note.contains("Android Equalizer", ignoreCase = true))
        assertTrue(tb375.note.contains("validated on real hardware", ignoreCase = true))
    }
}
