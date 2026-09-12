package kr.dwas.dwas_EQ.standardfx

import kr.dwas.dwas_EQ.domain.EqCurve


data class RoutedStandardFxSettings(
    val global: StandardFxSettings,
    val session: StandardFxSettings,
    val sessionFullDynamicsRouting: Boolean,
    val sessionForceVirtualizerMode: Boolean,
    val sessionEnvironmentalReverb: Boolean,
    val androidEqBassOverlayStrength: Int,
    val globalNativeBass: Boolean,
    val globalNativeVirtualizer: Boolean,
    val unavailableFailures: Map<String, String>,
)

object StandardFxRoutingPolicy {
    fun globalPrimarySettings(
        requested: StandardFxSettings,
        bassProxyDetected: Boolean = false,
        virtualizerProxyDetected: Boolean = false,
        fullSessionRouting: Boolean = false,
        directSessionStrengthRouting: Boolean = false,
    ): StandardFxSettings {
        if (fullSessionRouting || directSessionStrengthRouting) return StandardFxSettings()
        return requested.normalized().copy(
            reverbEnabled = false,
            reverbPreset = ReverbPreset.NONE,
        )
    }

    fun sessionFallbackSettings(
        requested: StandardFxSettings,
        globalFailedEffects: Set<String>,
        bassProxyDetected: Boolean = false,
        virtualizerProxyDetected: Boolean = false,
        fullSessionRouting: Boolean = false,
        directSessionStrengthRouting: Boolean = false,
    ): StandardFxSettings {
        if (directSessionStrengthRouting) {
            return requested.normalized().copy(
                attenuatorEnabled = false,
                channelBalanceEnabled = false,
                limiter = LimiterSettings(),
            )
        }
        if (fullSessionRouting) return requested.normalized()
        return requested.normalized().copy(
            attenuatorEnabled = false,
            channelBalanceEnabled = false,
            limiter = LimiterSettings(),
            bassBoostEnabled = requested.bassBoostEnabled && !bassProxyDetected && "Bass Boost" in globalFailedEffects,
            virtualizerEnabled = requested.virtualizerEnabled && !virtualizerProxyDetected && "Virtualizer" in globalFailedEffects,
        )
    }

    fun routeByProfile(
        requested: StandardFxSettings,
        profile: AudioCompatibilityProfile,
        lastAppliedCurve: EqCurve,
        globalFailedEffects: Set<String>,
    ): RoutedStandardFxSettings {
        val normalized = requested.normalized()
        val globalBass = profile.bass.route == AudioEffectRoute.GLOBAL_PUBLIC_BASS ||
            profile.bass.route == AudioEffectRoute.GLOBAL_DWAS_DYNAMICS
        val globalVirtualizer = profile.virtualizer.route == AudioEffectRoute.GLOBAL_PUBLIC_VIRTUALIZER ||
            profile.virtualizer.route == AudioEffectRoute.GLOBAL_DWAS_DYNAMICS
        val global = normalized.copy(
            attenuatorEnabled = normalized.attenuatorEnabled && profile.attenuator.route == AudioEffectRoute.GLOBAL_DYNAMICS,
            channelBalanceEnabled = normalized.channelBalanceEnabled && profile.balance.route == AudioEffectRoute.GLOBAL_DYNAMICS,
            limiter = normalized.limiter.copy(enabled = normalized.limiter.enabled && profile.limiter.route == AudioEffectRoute.GLOBAL_DYNAMICS),
            bassBoostEnabled = normalized.bassBoostEnabled && globalBass,
            virtualizerEnabled = normalized.virtualizerEnabled && globalVirtualizer,
            reverbEnabled = false,
            reverbPreset = ReverbPreset.NONE,
        )
        val bassSessionPrimary = profile.bass.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS ||
            profile.bass.route == AudioEffectRoute.SESSION_PUBLIC_BASS
        val virtualizerSessionPrimary = profile.virtualizer.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS ||
            profile.virtualizer.route == AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER ||
            profile.virtualizer.route == AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER_FORCED
        val bassSessionFallback = profile.bass.route == AudioEffectRoute.GLOBAL_PUBLIC_BASS && "Bass Boost" in globalFailedEffects
        val virtualizerSessionFallback = profile.virtualizer.route == AudioEffectRoute.GLOBAL_PUBLIC_VIRTUALIZER && "Virtualizer" in globalFailedEffects
        var session = normalized.copy(
            attenuatorEnabled = normalized.attenuatorEnabled && profile.attenuator.route == AudioEffectRoute.SESSION_DYNAMICS,
            channelBalanceEnabled = normalized.channelBalanceEnabled && profile.balance.route == AudioEffectRoute.SESSION_DYNAMICS,
            limiter = normalized.limiter.copy(enabled = normalized.limiter.enabled && profile.limiter.route == AudioEffectRoute.SESSION_DYNAMICS),
            bassBoostEnabled = normalized.bassBoostEnabled && (bassSessionPrimary || bassSessionFallback),
            virtualizerEnabled = normalized.virtualizerEnabled && (virtualizerSessionPrimary || virtualizerSessionFallback),
            reverbEnabled = normalized.reverbEnabled && (profile.reverb.route == AudioEffectRoute.SESSION_INSERT_REVERB || profile.reverb.route == AudioEffectRoute.SESSION_INSERT_ENV_REVERB),
            reverbPreset = if (profile.reverb.route == AudioEffectRoute.SESSION_INSERT_REVERB || profile.reverb.route == AudioEffectRoute.SESSION_INSERT_ENV_REVERB) normalized.reverbPreset else ReverbPreset.NONE,
        )
        val sessionFullDynamicsRouting = profile.bass.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS ||
            profile.virtualizer.route == AudioEffectRoute.SESSION_DWAS_DYNAMICS
        if (profile.usesSessionDynamics && session.attenuatorEnabled && session.automaticAttenuation) {
            session = session.copy(
                automaticAttenuation = false,
                manualAttenuationDb = HeadroomCalculator.attenuationDb(session, lastAppliedCurve),
            )
        }
        val unavailable = linkedMapOf<String, String>()
        fun unavailableIfRequested(enabled: Boolean, name: String, decision: AudioEffectRouteDecision) {
            if (enabled && decision.route == AudioEffectRoute.UNAVAILABLE) unavailable[name] = decision.detail
        }
        unavailableIfRequested(normalized.attenuatorEnabled, "Attenuator", profile.attenuator)
        unavailableIfRequested(normalized.channelBalanceEnabled, "Channel Balance", profile.balance)
        unavailableIfRequested(normalized.limiter.enabled, "Limiter", profile.limiter)
        unavailableIfRequested(normalized.bassBoostEnabled, "Bass Boost", profile.bass)
        unavailableIfRequested(normalized.virtualizerEnabled, "Virtualizer", profile.virtualizer)
        unavailableIfRequested(
            normalized.reverbEnabled && normalized.reverbPreset != ReverbPreset.NONE,
            "Preset Reverb",
            profile.reverb,
        )
        return RoutedStandardFxSettings(
            global = global,
            session = session,
            sessionFullDynamicsRouting = sessionFullDynamicsRouting,
            sessionForceVirtualizerMode = normalized.virtualizerEnabled && profile.virtualizer.route == AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER_FORCED,
            sessionEnvironmentalReverb = normalized.reverbEnabled && normalized.reverbPreset != ReverbPreset.NONE && profile.reverb.route == AudioEffectRoute.SESSION_INSERT_ENV_REVERB,
            androidEqBassOverlayStrength = if (normalized.bassBoostEnabled && profile.bass.route == AudioEffectRoute.ANDROID_EQ_BASS_OVERLAY) normalized.bassBoostStrength else 0,
            globalNativeBass = normalized.bassBoostEnabled && profile.bass.route == AudioEffectRoute.GLOBAL_DWAS_DYNAMICS,
            globalNativeVirtualizer = normalized.virtualizerEnabled && profile.virtualizer.route == AudioEffectRoute.GLOBAL_DWAS_DYNAMICS,
            unavailableFailures = unavailable,
        )
    }

    fun resolveSessionHeadroom(
        requested: StandardFxSettings,
        lastAppliedCurve: EqCurve,
        fullSessionRouting: Boolean,
    ): StandardFxSettings {
        val normalized = requested.normalized()
        if (!fullSessionRouting || !normalized.attenuatorEnabled || !normalized.automaticAttenuation) return normalized
        return normalized.copy(
            automaticAttenuation = false,
            manualAttenuationDb = HeadroomCalculator.attenuationDb(normalized, lastAppliedCurve),
        )
    }
}
