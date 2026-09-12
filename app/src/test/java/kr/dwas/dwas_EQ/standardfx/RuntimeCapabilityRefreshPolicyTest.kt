package kr.dwas.dwas_EQ.standardfx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeCapabilityRefreshPolicyTest {
    @Test fun freshProcessMustProbeStandardFxEvenWhenSavedEffectsRequireRuntimeHold() {
        val empty = StandardFxCapabilities()
        assertFalse(RuntimeCapabilityRefreshPolicy.shouldPreserveStandardFxCapabilities(true, empty))
    }

    @Test fun initializedRuntimeCapabilitiesMayBePreservedWhileEffectsAreActive() {
        val initialized = StandardFxCapabilities(
            bassBoost = true,
            virtualizer = true,
            details = mapOf("Capability probe" to "passive descriptor/profile evaluation"),
        )
        assertTrue(RuntimeCapabilityRefreshPolicy.shouldPreserveStandardFxCapabilities(true, initialized))
        assertFalse(RuntimeCapabilityRefreshPolicy.shouldPreserveStandardFxCapabilities(false, initialized))
    }
    @Test fun persistedEqOwnershipMustPreserveBackendProbeStateAcrossProcessRestart() {
        assertTrue(RuntimeCapabilityRefreshPolicy.shouldPreserveBackendProbes(false, true, false, false))
        assertTrue(RuntimeCapabilityRefreshPolicy.shouldPreserveBackendProbes(false, false, true, false))
        assertTrue(RuntimeCapabilityRefreshPolicy.shouldPreserveBackendProbes(true, false, false, false))
        assertFalse(RuntimeCapabilityRefreshPolicy.shouldPreserveBackendProbes(false, false, false, false))
        assertFalse(RuntimeCapabilityRefreshPolicy.shouldPreserveBackendProbes(true, true, true, true))
    }

}
