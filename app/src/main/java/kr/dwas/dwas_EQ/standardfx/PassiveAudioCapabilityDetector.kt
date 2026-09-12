package kr.dwas.dwas_EQ.standardfx

object PassiveAudioCapabilityDetector {
    fun capabilities(
        profile: AudioCompatibilityProfile,
        routingDetail: String,
    ): StandardFxCapabilities = StandardFxCapabilities(
        dynamicsProcessing = profile.usesDynamicsProcessing,
        bassBoost = profile.bass.route != AudioEffectRoute.UNAVAILABLE,
        virtualizer = profile.virtualizer.route != AudioEffectRoute.UNAVAILABLE,
        presetReverb = profile.reverb.route != AudioEffectRoute.UNAVAILABLE,
        details = linkedMapOf(
            "Capability probe" to "passive descriptor/profile evaluation",
            "Session routing" to routingDetail,
            "Attenuator route" to profile.attenuator.route.name,
            "Channel Balance route" to profile.balance.route.name,
            "Limiter route" to profile.limiter.route.name,
            "Bass Boost route" to profile.bass.route.name,
            "Bass Boost safety" to profile.bass.safety.name,
            "Virtualizer route" to profile.virtualizer.route.name,
            "Virtualizer safety" to profile.virtualizer.safety.name,
            "Preset Reverb route" to profile.reverb.route.name,
            "Preset Reverb safety" to profile.reverb.safety.name,
        ),
    )
}
