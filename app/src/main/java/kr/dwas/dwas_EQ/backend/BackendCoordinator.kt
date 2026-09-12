package kr.dwas.dwas_EQ.backend

import android.content.Context

class BackendCoordinator(context: Context, rootAllowed: () -> Boolean) {
    val wiredAdb = WiredAdbDolbyBackend(context)
    val direct = DirectDolbyBackend()
    val root = RootDolbyBackend(context, rootAllowed)
    val androidEq = AndroidEqualizerBackend(context)

    fun allProbes(): List<BackendProbe> = listOf(
        wiredAdb.probe(),
        direct.probe(),
        root.probe(),
        androidEq.probe(),
    )

    fun backend(kind: BackendKind): EqBackend = when (kind) {
        BackendKind.WIRED_ADB_DOLBY -> wiredAdb
        BackendKind.DIRECT_DOLBY -> direct
        BackendKind.ROOT_DOLBY -> root
        BackendKind.ANDROID_EQUALIZER -> androidEq
    }

    fun candidates(preference: BackendPreference, rootOptIn: Boolean): List<EqBackend> =
        BackendPolicy.order(preference, rootOptIn).map(::backend)

    fun select(preference: BackendPreference, rootOptIn: Boolean): Pair<EqBackend?, List<BackendProbe>> {
        val probes = mutableListOf<BackendProbe>()
        for (backend in candidates(preference, rootOptIn)) {
            val probe = backend.probe()
            probes += probe
            if (probe.available && probe.hasControl) return backend to probes
        }
        return null to probes
    }

    fun safeProbe(backend: EqBackend): BackendResult<Unit> = when (backend) {
        is WiredAdbDolbyBackend -> backend.safeProbe()
        is RootDolbyBackend -> backend.safeProbe()
        is DirectDolbyBackend -> backend.safeProbe()
        else -> BackendResult(false, message = "Safe DAP probe is only available for Dolby backends.")
    }

    fun releaseControlAccess() {
        wiredAdb.close()
        direct.close()
        root.close()
    }

    fun close() {
        releaseControlAccess()
        androidEq.close()
    }
}
