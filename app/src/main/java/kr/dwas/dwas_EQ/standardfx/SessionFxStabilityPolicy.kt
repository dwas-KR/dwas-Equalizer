package kr.dwas.dwas_EQ.standardfx

object SessionFxStabilityPolicy {
    val verificationRetryDelaysMs: LongArray
        get() = longArrayOf(0L, 20L, 40L, 80L)

    const val virtualizerActivationWarmupMs: Long = 500L
    private const val bassOverlayReadbackToleranceMb: Int = 50
    private const val equalizerReadbackToleranceMb: Int = 50

    fun shouldTrySoftware(proxyDetected: Boolean): Boolean = proxyDetected

    fun bassOverlayNativeReadbackMatches(expected: Int, actual: Int): Boolean =
        kotlin.math.abs(expected - actual) <= bassOverlayReadbackToleranceMb

    fun equalizerNativeReadbackMatches(expected: Int, actual: Int): Boolean =
        kotlin.math.abs(expected - actual) <= equalizerReadbackToleranceMb

    fun shouldWarmNewSpeakerVirtualizer(
        newlyOpened: Boolean,
        speakerSafeRouting: Boolean,
        requestedStrength: Int,
    ): Boolean = newlyOpened && speakerSafeRouting && requestedStrength > 0

    fun curveRequiresStandardFxReapply(attenuatorEnabled: Boolean, automaticAttenuation: Boolean): Boolean =
        attenuatorEnabled && automaticAttenuation

    fun shouldPrimeNeutral(strengthSupported: Boolean, currentStrength: Int): Boolean =
        strengthSupported && currentStrength < 0

    fun shouldEnableFixedStrength(strengthSupported: Boolean, requestedStrength: Int): Boolean =
        !strengthSupported && requestedStrength > 0

    fun shouldForceSpeakerVirtualizationMode(
        speakerSafeRouting: Boolean,
        requestedStrength: Int,
        speakerModePrimed: Boolean,
    ): Boolean = speakerSafeRouting && requestedStrength > 0 && !speakerModePrimed

    fun shouldReuseEnvironmentalReverb(
        hasControl: Boolean,
        enabled: Boolean,
        activePreset: ReverbPreset,
        requestedPreset: ReverbPreset,
    ): Boolean =
        hasControl && enabled && requestedPreset != ReverbPreset.NONE && activePreset == requestedPreset

    fun environmentalReverbReadbackMatches(parameter: Int, expected: Int, actual: Int): Boolean = when (parameter) {
        0, 1, 2, 3 -> actual == expected
        4 -> actual in -9000..1000
        5 -> actual in 0..300
        6 -> actual in -9000..2000
        7 -> actual in 0..100
        8, 9 -> actual in 0..1000
        else -> false
    }
}
