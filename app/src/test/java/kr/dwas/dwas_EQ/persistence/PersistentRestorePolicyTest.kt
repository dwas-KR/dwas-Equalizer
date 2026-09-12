package kr.dwas.dwas_EQ.persistence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentRestorePolicyTest {
    @Test
    fun service_starts_only_when_enabled_and_armed() {
        assertFalse(PersistentRestorePolicy.shouldStartService(false, false))
        assertFalse(PersistentRestorePolicy.shouldStartService(true, false))
        assertFalse(PersistentRestorePolicy.shouldStartService(false, true))
        assertTrue(PersistentRestorePolicy.shouldStartService(true, true))
    }

    @Test
    fun only_explicit_user_apply_may_write_dolby_after_reboot() {
        assertFalse(PersistentRestorePolicy.mayWrite(PersistentTrigger.BOOT))
        assertFalse(PersistentRestorePolicy.mayWrite(PersistentTrigger.PACKAGE_REPLACED))
        assertFalse(PersistentRestorePolicy.mayWrite(PersistentTrigger.SERVICE_START))
        assertFalse(PersistentRestorePolicy.mayWrite(PersistentTrigger.ROUTE_CHANGE))
        assertFalse(PersistentRestorePolicy.mayWrite(PersistentTrigger.PLAYBACK_CHANGE))
        assertTrue(PersistentRestorePolicy.mayWrite(PersistentTrigger.USER_APPLY))
    }
}
