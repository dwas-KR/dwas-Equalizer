package kr.dwas.dwas_EQ.backend

object ControlAccessResetGatePolicy {
    fun isMasterToggleInteractive(controlAccessReset: Boolean): Boolean = !controlAccessReset

    fun shouldUnlockAfterVerifiedWiredAdb(controlAccessReset: Boolean, wiredAdbReady: Boolean): Boolean =
        controlAccessReset && wiredAdbReady

    fun shouldReactivateMasterAfterVerifiedWiredAdb(controlAccessReset: Boolean, wiredAdbReady: Boolean): Boolean =
        controlAccessReset && wiredAdbReady
}
