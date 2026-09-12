package kr.dwas.dwas_EQ.persistence

enum class PersistentTrigger {
    BOOT,
    PACKAGE_REPLACED,
    SERVICE_START,
    ROUTE_CHANGE,
    PLAYBACK_CHANGE,
    USER_APPLY,
}

object PersistentRestorePolicy {
    fun shouldStartService(enabled: Boolean, armed: Boolean): Boolean = enabled && armed

    fun shouldStartAtBoot(
        bootPersistenceEnabled: Boolean,
        dolbyArmed: Boolean,
        standardFxEnabled: Boolean,
        androidEqEnabled: Boolean = false,
    ): Boolean = bootPersistenceEnabled && (dolbyArmed || standardFxEnabled || androidEqEnabled)

    fun shouldKeepServiceAlive(
        dolbyPersistenceActive: Boolean,
        standardFxEnabled: Boolean,
        androidEqEnabled: Boolean = false,
    ): Boolean = dolbyPersistenceActive || standardFxEnabled || androidEqEnabled

    fun mayWrite(trigger: PersistentTrigger): Boolean = trigger == PersistentTrigger.USER_APPLY
}
