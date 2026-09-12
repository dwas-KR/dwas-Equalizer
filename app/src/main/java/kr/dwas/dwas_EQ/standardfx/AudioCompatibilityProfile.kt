package kr.dwas.dwas_EQ.standardfx

enum class AudioEffectSafety {
    VERIFIED_SAFE,
    STATIC_SUPPORTED,
    KNOWN_UNSAFE,
    UNKNOWN,
}

enum class AudioEffectRoute {
    GLOBAL_DYNAMICS,
    GLOBAL_DWAS_DYNAMICS,
    GLOBAL_PUBLIC_BASS,
    GLOBAL_PUBLIC_VIRTUALIZER,
    SESSION_DYNAMICS,
    SESSION_DWAS_DYNAMICS,
    SESSION_PUBLIC_BASS,
    SESSION_PUBLIC_VIRTUALIZER,
    SESSION_PUBLIC_VIRTUALIZER_FORCED,
    SESSION_INSERT_REVERB,
    SESSION_INSERT_ENV_REVERB,
    ANDROID_EQ_BASS_OVERLAY,
    UNAVAILABLE,
}

data class AudioEffectRouteDecision(
    val route: AudioEffectRoute,
    val safety: AudioEffectSafety,
    val detail: String,
)

data class AudioCompatibilityProfile(
    val attenuator: AudioEffectRouteDecision,
    val balance: AudioEffectRouteDecision,
    val limiter: AudioEffectRouteDecision,
    val bass: AudioEffectRouteDecision,
    val virtualizer: AudioEffectRouteDecision,
    val reverb: AudioEffectRouteDecision,
    val allowVendorProxyStrengthEffects: Boolean = false,
    val preferDirectSoftwareStrengthEffects: Boolean = false,
) {
    val usesDynamicsProcessing: Boolean
        get() = listOf(attenuator, balance, limiter, bass, virtualizer).any {
            it.route == AudioEffectRoute.GLOBAL_DYNAMICS ||
                it.route == AudioEffectRoute.GLOBAL_DWAS_DYNAMICS ||
                it.route == AudioEffectRoute.SESSION_DYNAMICS ||
                it.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS
        }

    val usesSessionDynamics: Boolean
        get() = listOf(attenuator, balance, limiter, bass, virtualizer).any {
            it.route == AudioEffectRoute.SESSION_DYNAMICS || it.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS
        }

    val usesSessionEffects: Boolean
        get() = listOf(attenuator, balance, limiter, bass, virtualizer, reverb).any {
            it.route == AudioEffectRoute.SESSION_DYNAMICS ||
                it.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS ||
                it.route == AudioEffectRoute.SESSION_PUBLIC_BASS ||
                it.route == AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER ||
                it.route == AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER_FORCED ||
                it.route == AudioEffectRoute.SESSION_INSERT_REVERB ||
                it.route == AudioEffectRoute.SESSION_INSERT_ENV_REVERB
        }
}
