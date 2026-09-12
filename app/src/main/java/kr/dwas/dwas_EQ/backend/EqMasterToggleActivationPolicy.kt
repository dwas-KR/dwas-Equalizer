package kr.dwas.dwas_EQ.backend

object EqMasterToggleActivationPolicy {
    fun shouldDeferAndroidEq(
        preference: BackendPreference,
        rootOptIn: Boolean,
        activeMediaSessionAvailable: Boolean,
    ): Boolean = !activeMediaSessionAvailable &&
        BackendPolicy.strategyOrder(preference, rootOptIn).contains(BackendStrategy.ANDROID_EQUALIZER)
}
