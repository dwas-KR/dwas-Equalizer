package kr.dwas.dwas_EQ.backend

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendPolicyTest {
    @Test fun autoSkipsRootWithoutExplicitOptIn() {
        assertEquals(
            listOf(BackendKind.WIRED_ADB_DOLBY, BackendKind.DIRECT_DOLBY, BackendKind.ANDROID_EQUALIZER),
            BackendPolicy.order(BackendPreference.AUTO, rootOptIn = false),
        )
    }

    @Test fun autoIncludesRootOnlyAfterExplicitOptIn() {
        assertEquals(
            listOf(BackendKind.WIRED_ADB_DOLBY, BackendKind.DIRECT_DOLBY, BackendKind.ROOT_DOLBY, BackendKind.ANDROID_EQUALIZER),
            BackendPolicy.order(BackendPreference.AUTO, rootOptIn = true),
        )
    }
}
