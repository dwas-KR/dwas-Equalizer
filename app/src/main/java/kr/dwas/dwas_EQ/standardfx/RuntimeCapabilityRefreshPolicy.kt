package kr.dwas.dwas_EQ.standardfx

object RuntimeCapabilityRefreshPolicy {
    fun shouldPreserveBackendProbes(
        activeRuntimeTransaction: Boolean,
        persistentEqArmed: Boolean,
        androidEqArmed: Boolean,
        controlAccessReset: Boolean,
    ): Boolean = !controlAccessReset && (activeRuntimeTransaction || persistentEqArmed || androidEqArmed)

    fun shouldPreserveStandardFxCapabilities(
        runtimeActive: Boolean,
        capabilities: StandardFxCapabilities,
    ): Boolean = runtimeActive && capabilities.details.isNotEmpty()
}
