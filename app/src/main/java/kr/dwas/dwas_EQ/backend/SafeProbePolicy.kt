package kr.dwas.dwas_EQ.backend

object SafeProbePolicy {
    fun isAvailable(
        probes: List<BackendProbe>,
        preference: BackendPreference,
        rootOptIn: Boolean,
        dolbyRouteAllowed: Boolean,
    ): Boolean {
        if (!dolbyRouteAllowed) return false
        val allowed = BackendPolicy.order(preference, rootOptIn).toSet()
        return probes.any { probe ->
            probe.backend in allowed &&
                probe.backend != BackendKind.ANDROID_EQUALIZER &&
                probe.available && probe.hasControl
        }
    }
}
