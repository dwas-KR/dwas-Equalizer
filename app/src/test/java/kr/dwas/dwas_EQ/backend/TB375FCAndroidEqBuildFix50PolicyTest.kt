package kr.dwas.dwas_EQ.backend

import kr.dwas.dwas_EQ.device.DeviceCompatibilityDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB375FCAndroidEqBuildFix50PolicyTest {
    @Test fun tb375AutoBypassesInactiveDolbyGeq() {
        val device = checkNotNull(DeviceCompatibilityDatabase.find("TB375FC"))
        assertTrue(device.dolbyGeqRuntimeInactive)
        assertEquals(
            listOf(BackendStrategy.ANDROID_EQUALIZER),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.AUTO, device.dolbyGeqRuntimeInactive), rootOptIn = false),
        )
    }

    @Test fun explicitBackendChoicesRemainUnchanged() {
        assertEquals(
            listOf(BackendStrategy.PERSISTENT_DIRECT, BackendStrategy.TRANSIENT_DIRECT),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.DIRECT, true), rootOptIn = false),
        )
        assertEquals(
            listOf(BackendStrategy.WIRED_ADB),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.WIRED_ADB, true), rootOptIn = false),
        )
    }

    @Test fun unaffectedModelsKeepOriginalAutoOrder() {
        val tb365 = checkNotNull(DeviceCompatibilityDatabase.find("TB365FC"))
        assertFalse(tb365.dolbyGeqRuntimeInactive)
        assertEquals(
            listOf(
                BackendStrategy.PERSISTENT_DIRECT,
                BackendStrategy.WIRED_ADB,
                BackendStrategy.TRANSIENT_DIRECT,
                BackendStrategy.ANDROID_EQUALIZER,
            ),
            BackendPolicy.strategyOrder(BackendPolicy.effectivePreference(BackendPreference.AUTO, tb365.dolbyGeqRuntimeInactive), rootOptIn = false),
        )
    }
}
