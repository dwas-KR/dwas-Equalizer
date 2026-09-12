package kr.dwas.dwas_EQ.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeProbePolicyTest {
    private fun probe(kind: BackendKind, ok: Boolean) = BackendProbe(kind, ok, ok, if (ok) 20 else null, "test")

    @Test fun requiresControllableDolbyInSelectedOrderAndSafeRoute() {
        val probes = listOf(probe(BackendKind.WIRED_ADB_DOLBY, true), probe(BackendKind.ANDROID_EQUALIZER, true))
        assertTrue(SafeProbePolicy.isAvailable(probes, BackendPreference.AUTO, false, true))
        assertFalse(SafeProbePolicy.isAvailable(probes, BackendPreference.AUTO, false, false))
        assertFalse(SafeProbePolicy.isAvailable(probes, BackendPreference.ANDROID_EQ, false, true))
    }
}
