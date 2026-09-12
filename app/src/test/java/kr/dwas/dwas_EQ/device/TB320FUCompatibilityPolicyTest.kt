package kr.dwas.dwas_EQ.device

import kr.dwas.dwas_EQ.standardfx.AudioEffectRoute
import kr.dwas.dwas_EQ.standardfx.AudioEffectSafety
import kr.dwas.dwas_EQ.standardfx.EffectRouteResolver
import kr.dwas.dwas_EQ.standardfx.StandardFxCompatibilityPolicy
import kr.dwas.dwas_EQ.standardfx.StandardFxTopologyEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TB320FUCompatibilityPolicyTest {
    @Test
    fun archiveAliasResolvesToCapturedTb320fcIdentity() {
        val canonical = DeviceCompatibilityDatabase.find("TB320FC")!!
        val archiveAlias = DeviceCompatibilityDatabase.find("TB320FU")!!
        assertSame(canonical, archiveAlias)
        assertEquals(15, canonical.androidVersion)
        assertEquals("taro", canonical.platform)
        assertEquals("qcom", canonical.hardware)
        assertEquals(15, canonical.daxGeneration)
        assertTrue(canonical.tuningCaptured)
        assertTrue(canonical.nativeFxVerified)
    }

    @Test
    fun qtiProxyTopologyUsesDwasDynamicsWithVerifiedDeviceState() {
        val evidence = StandardFxTopologyEvidence(
            qualcomm = true,
            qtiBassProxy = true,
            qtiVirtualizerProxy = true,
            directSoftwareBass = false,
            directSoftwareVirtualizer = false,
            directSoftwareDynamics = true,
            knownNativeFxVerified = true,
        )
        val profile = EffectRouteResolver.resolve(evidence, presetReverbDescriptorPresent = true)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.bass.route)
        assertEquals(AudioEffectRoute.GLOBAL_DWAS_DYNAMICS, profile.virtualizer.route)
        assertEquals(AudioEffectSafety.VERIFIED_SAFE, profile.bass.safety)
        assertEquals(AudioEffectSafety.VERIFIED_SAFE, profile.virtualizer.safety)
        assertFalse(StandardFxCompatibilityPolicy.routeFullStandardFxToPlaybackSession(evidence))
    }
}
