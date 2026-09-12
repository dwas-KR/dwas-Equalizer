package kr.dwas.dwas_EQ.backend

import kr.dwas.dwas_EQ.device.DeviceCompatibilityDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB373FUAndroidEqBuildFix63PolicyTest {
    @Test fun tb373AutoBypassesInactiveDolbyGeq() {
        val device = checkNotNull(DeviceCompatibilityDatabase.find("TB373FU"))
        assertTrue(device.dolbyGeqRuntimeInactive)
        assertTrue(device.speakerSafeFourFx)
        assertTrue(device.noDynamicsProcessingStandardFx)
        assertFalse(device.nativeFxVerified)
        assertEquals(
            listOf(BackendStrategy.ANDROID_EQUALIZER),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.AUTO, device.dolbyGeqRuntimeInactive), rootOptIn = false),
        )
    }

    @Test fun explicitDiagnosticBackendChoicesRemainAvailable() {
        assertEquals(
            listOf(BackendStrategy.PERSISTENT_DIRECT, BackendStrategy.TRANSIENT_DIRECT),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.DIRECT, true), rootOptIn = false),
        )
        assertEquals(
            listOf(BackendStrategy.WIRED_ADB),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.WIRED_ADB, true), rootOptIn = false),
        )
    }

    @Test fun validatedTb365KeepsOriginalAutoOrder() {
        val device = checkNotNull(DeviceCompatibilityDatabase.find("TB365FC"))
        assertFalse(device.dolbyGeqRuntimeInactive)
        assertEquals(
            listOf(
                BackendStrategy.PERSISTENT_DIRECT,
                BackendStrategy.WIRED_ADB,
                BackendStrategy.TRANSIENT_DIRECT,
                BackendStrategy.ANDROID_EQUALIZER,
            ),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.AUTO, device.dolbyGeqRuntimeInactive), rootOptIn = false),
        )
    }
}
