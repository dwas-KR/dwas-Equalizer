package kr.dwas.dwas_EQ.backend

enum class BackendStrategy {
    PERSISTENT_DIRECT,
    WIRED_ADB,
    TRANSIENT_DIRECT,
    ROOT,
    ANDROID_EQUALIZER,
}

object BackendPolicy {
    fun effectivePreference(preference: BackendPreference, dolbyGeqRuntimeInactive: Boolean): BackendPreference =
        if (preference == BackendPreference.AUTO && dolbyGeqRuntimeInactive) BackendPreference.ANDROID_EQ else preference

    fun strategyOrder(preference: BackendPreference, rootOptIn: Boolean): List<BackendStrategy> = when (preference) {
        BackendPreference.AUTO -> buildList {
            add(BackendStrategy.PERSISTENT_DIRECT)
            add(BackendStrategy.WIRED_ADB)
            add(BackendStrategy.TRANSIENT_DIRECT)
            if (rootOptIn) add(BackendStrategy.ROOT)
            add(BackendStrategy.ANDROID_EQUALIZER)
        }
        BackendPreference.WIRED_ADB -> listOf(BackendStrategy.WIRED_ADB)
        BackendPreference.DIRECT -> listOf(BackendStrategy.PERSISTENT_DIRECT, BackendStrategy.TRANSIENT_DIRECT)
        BackendPreference.ROOT -> if (rootOptIn) listOf(BackendStrategy.ROOT) else emptyList()
        BackendPreference.ANDROID_EQ -> listOf(BackendStrategy.ANDROID_EQUALIZER)
    }

    fun order(preference: BackendPreference, rootOptIn: Boolean): List<BackendKind> =
        strategyOrder(preference, rootOptIn).mapNotNull { strategy ->
            when (strategy) {
                BackendStrategy.PERSISTENT_DIRECT -> null
                BackendStrategy.WIRED_ADB -> BackendKind.WIRED_ADB_DOLBY
                BackendStrategy.TRANSIENT_DIRECT -> BackendKind.DIRECT_DOLBY
                BackendStrategy.ROOT -> BackendKind.ROOT_DOLBY
                BackendStrategy.ANDROID_EQUALIZER -> BackendKind.ANDROID_EQUALIZER
            }
        }
}
