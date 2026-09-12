package kr.dwas.dwas_EQ.standardfx

object EffectRouteResolver {
    fun resolve(
        evidence: StandardFxTopologyEvidence,
        presetReverbDescriptorPresent: Boolean,
        environmentalReverbDescriptorPresent: Boolean = false,
    ): AudioCompatibilityProfile {
        if (evidence.knownNoDynamicsProcessingQuirk) {
            val coreDynamics = decision(
                AudioEffectRoute.UNAVAILABLE,
                AudioEffectSafety.KNOWN_UNSAFE,
                "DynamicsProcessing blocked by verified runtime or topology quirk",
            )
            val bass = when {
                evidence.knownSpeakerSafeFourFxQuirk -> decision(AudioEffectRoute.ANDROID_EQ_BASS_OVERLAY, AudioEffectSafety.VERIFIED_SAFE, "Built-in speaker BassBoost bypassed through dwas_EQ Equalizer bass overlay")
                evidence.directSoftwareBass && !evidence.qtiBassProxy -> decision(AudioEffectRoute.SESSION_PUBLIC_BASS, AudioEffectSafety.VERIFIED_SAFE, "Verified direct playback-session BassBoost route")
                else -> decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.KNOWN_UNSAFE, "No safe direct playback-session BassBoost route")
            }
            val virtualizer = when {
                evidence.knownSpeakerSafeFourFxQuirk && evidence.directSoftwareVirtualizer && !evidence.qtiVirtualizerProxy -> decision(AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER_FORCED, AudioEffectSafety.VERIFIED_SAFE, "Playback-session Virtualizer with explicit speaker-compatible mode")
                evidence.directSoftwareVirtualizer && !evidence.qtiVirtualizerProxy -> decision(AudioEffectRoute.SESSION_PUBLIC_VIRTUALIZER, AudioEffectSafety.VERIFIED_SAFE, "Verified direct playback-session Virtualizer route")
                else -> decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.KNOWN_UNSAFE, "No safe direct playback-session Virtualizer route")
            }
            val reverb = if (evidence.knownSpeakerSafeFourFxQuirk && environmentalReverbDescriptorPresent) {
                decision(AudioEffectRoute.SESSION_INSERT_ENV_REVERB, AudioEffectSafety.VERIFIED_SAFE, "dwas_EQ environmental insert reverb fallback")
            } else {
                reverbDecision(presetReverbDescriptorPresent, AudioEffectSafety.STATIC_SUPPORTED)
            }
            return AudioCompatibilityProfile(
                attenuator = coreDynamics,
                balance = coreDynamics,
                limiter = coreDynamics,
                bass = bass,
                virtualizer = virtualizer,
                reverb = reverb,
            )
        }

        if (evidence.knownIsolatedSessionStrengthFx) {
            val dynamics = if (evidence.directSoftwareDynamics) {
                decision(AudioEffectRoute.SESSION_DYNAMICS, AudioEffectSafety.STATIC_SUPPORTED, "Playback-session DynamicsProcessing retained for Headroom, Channel Balance and Limiter")
            } else {
                decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "Playback-session DynamicsProcessing unavailable")
            }
            val bass = decision(
                AudioEffectRoute.ANDROID_EQ_BASS_OVERLAY,
                AudioEffectSafety.STATIC_SUPPORTED,
                "dwas_EQ Equalizer bass overlay avoids the failed TB371FC QTI proxy and nested software BassBoost routes",
            )
            val virtualizer = decision(
                AudioEffectRoute.UNAVAILABLE,
                AudioEffectSafety.KNOWN_UNSAFE,
                "TB371FC Virtualizer is fail-closed after DynamicsProcessing, QTI proxy and nested software implementation routes failed real-device validation",
            )
            return AudioCompatibilityProfile(
                attenuator = dynamics,
                balance = dynamics,
                limiter = dynamics,
                bass = bass,
                virtualizer = virtualizer,
                reverb = reverbDecision(presetReverbDescriptorPresent, AudioEffectSafety.STATIC_SUPPORTED),
            )
        }

        if (evidence.knownFullSessionQuirk) {
            val dynamicsAvailable = evidence.directSoftwareDynamics || evidence.knownFullSessionQuirk
            val dynamics = if (dynamicsAvailable) {
                decision(AudioEffectRoute.SESSION_DYNAMICS, AudioEffectSafety.VERIFIED_SAFE, "Verified playback-session DynamicsProcessing route")
            } else {
                decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "Playback-session DynamicsProcessing unavailable")
            }
            val bass = if (dynamicsAvailable) {
                decision(AudioEffectRoute.SESSION_DWAS_DYNAMICS, AudioEffectSafety.VERIFIED_SAFE, "dwas_EQ Bass routed through playback-session DynamicsProcessing")
            } else {
                decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "Playback-session Bass route unavailable")
            }
            val virtualizer = if (dynamicsAvailable) {
                decision(AudioEffectRoute.SESSION_DWAS_DYNAMICS, AudioEffectSafety.VERIFIED_SAFE, "dwas_EQ Virtualizer routed through playback-session DynamicsProcessing")
            } else {
                decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "Playback-session Virtualizer route unavailable")
            }
            return AudioCompatibilityProfile(
                attenuator = dynamics,
                balance = dynamics,
                limiter = dynamics,
                bass = bass,
                virtualizer = virtualizer,
                reverb = reverbDecision(presetReverbDescriptorPresent, AudioEffectSafety.STATIC_SUPPORTED),
            )
        }

        val globalDynamics = if (evidence.directSoftwareDynamics) {
            decision(AudioEffectRoute.GLOBAL_DYNAMICS, AudioEffectSafety.STATIC_SUPPORTED, "Global DynamicsProcessing descriptor present")
        } else {
            decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "DynamicsProcessing descriptor unavailable")
        }
        val bass = when {
            evidence.knownPublicBassBoostRuntimeInactive && evidence.directSoftwareDynamics -> decision(
                AudioEffectRoute.GLOBAL_DWAS_DYNAMICS,
                if (evidence.knownNativeFxVerified) AudioEffectSafety.VERIFIED_SAFE else AudioEffectSafety.STATIC_SUPPORTED,
                "Runtime-inactive public BassBoost bypassed through dwas_EQ DynamicsProcessing",
            )
            evidence.qtiBassProxy && evidence.directSoftwareDynamics -> decision(
                AudioEffectRoute.GLOBAL_DWAS_DYNAMICS,
                if (evidence.knownNativeFxVerified) AudioEffectSafety.VERIFIED_SAFE else AudioEffectSafety.STATIC_SUPPORTED,
                "Unsafe vendor BassBoost proxy bypassed through dwas_EQ DynamicsProcessing",
            )
            evidence.directSoftwareBass && !evidence.qtiBassProxy -> decision(
                AudioEffectRoute.GLOBAL_PUBLIC_BASS,
                AudioEffectSafety.STATIC_SUPPORTED,
                "Direct software BassBoost descriptor present",
            )
            else -> decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "No safe BassBoost route detected")
        }
        val virtualizer = when {
            evidence.knownPublicVirtualizerRuntimeInactive && evidence.directSoftwareDynamics -> decision(
                AudioEffectRoute.GLOBAL_DWAS_DYNAMICS,
                if (evidence.knownNativeFxVerified) AudioEffectSafety.VERIFIED_SAFE else AudioEffectSafety.STATIC_SUPPORTED,
                "Runtime-inactive public Virtualizer bypassed through dwas_EQ DynamicsProcessing",
            )
            evidence.qtiVirtualizerProxy && evidence.directSoftwareDynamics -> decision(
                AudioEffectRoute.GLOBAL_DWAS_DYNAMICS,
                if (evidence.knownNativeFxVerified) AudioEffectSafety.VERIFIED_SAFE else AudioEffectSafety.STATIC_SUPPORTED,
                "Unsafe vendor Virtualizer proxy bypassed through dwas_EQ DynamicsProcessing",
            )
            evidence.directSoftwareVirtualizer && !evidence.qtiVirtualizerProxy -> decision(
                AudioEffectRoute.GLOBAL_PUBLIC_VIRTUALIZER,
                AudioEffectSafety.STATIC_SUPPORTED,
                "Direct software Virtualizer descriptor present",
            )
            else -> decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "No safe Virtualizer route detected")
        }
        return AudioCompatibilityProfile(
            attenuator = globalDynamics,
            balance = globalDynamics,
            limiter = globalDynamics,
            bass = bass,
            virtualizer = virtualizer,
            reverb = reverbDecision(presetReverbDescriptorPresent, AudioEffectSafety.STATIC_SUPPORTED),
        )
    }

    private fun reverbDecision(present: Boolean, safety: AudioEffectSafety): AudioEffectRouteDecision =
        if (present) decision(AudioEffectRoute.SESSION_INSERT_REVERB, safety, "Insert Preset Reverb descriptor present")
        else decision(AudioEffectRoute.UNAVAILABLE, AudioEffectSafety.UNKNOWN, "Preset Reverb descriptor unavailable")

    private fun decision(
        route: AudioEffectRoute,
        safety: AudioEffectSafety,
        detail: String,
    ) = AudioEffectRouteDecision(route, safety, detail)
}
