package kr.dwas.dwas_EQ.standardfx

import kr.dwas.dwas_EQ.domain.EqCurve

object PeridotUnifiedEqCorePolicy {
    val CENTER_FREQUENCIES_HZ = intArrayOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    val CUTOFF_FREQUENCIES_HZ = floatArrayOf(89f, 177f, 354f, 707f, 1414f, 2828f, 5657f, 11314f, 20000f)

    fun shouldUse(
        profile: AudioCompatibilityProfile,
        settings: StandardFxSettings,
        androidEqLogicalTargetAvailable: Boolean,
    ): Boolean {
        val sharedEqCoreRoute = profile.attenuator.route == AudioEffectRoute.SESSION_DYNAMICS &&
            profile.balance.route == AudioEffectRoute.SESSION_DYNAMICS &&
            profile.limiter.route == AudioEffectRoute.SESSION_DYNAMICS &&
            profile.bass.route == AudioEffectRoute.ANDROID_EQ_BASS_OVERLAY
        val coreRequested = settings.attenuatorEnabled || settings.channelBalanceEnabled || settings.limiter.enabled
        return sharedEqCoreRoute && coreRequested && androidEqLogicalTargetAvailable
    }

    fun combinedBandGains(curve: EqCurve, bassStrength: Int): FloatArray =
        FloatArray(EqCurve.BAND_COUNT) { index ->
            curve.gainsDb[index] + SpeakerSafeBassOverlayPolicy.boostDb(CENTER_FREQUENCIES_HZ[index], bassStrength)
        }
}
