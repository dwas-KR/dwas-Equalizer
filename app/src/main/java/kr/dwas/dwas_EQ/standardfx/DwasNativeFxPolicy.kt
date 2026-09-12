package kr.dwas.dwas_EQ.standardfx

object DwasNativeFxPolicy {
    const val PRE_EQ_BAND_COUNT = 5
    val PRE_EQ_CUTOFFS_HZ = floatArrayOf(120f, 250f, 900f, 3500f, 20000f)

    data class StereoPreEqPlan(
        val leftGainsDb: FloatArray,
        val rightGainsDb: FloatArray,
    )

    fun useNativeBass(enabled: Boolean, unsafeProxyDetected: Boolean): Boolean =
        enabled && unsafeProxyDetected

    fun useNativeVirtualizer(enabled: Boolean, unsafeProxyDetected: Boolean): Boolean =
        enabled && unsafeProxyDetected

    fun stereoPreEqPlan(
        bassEnabled: Boolean,
        bassStrength: Int,
        virtualizerEnabled: Boolean,
        virtualizerStrength: Int,
    ): StereoPreEqPlan {
        val bass = bassStrength.coerceIn(0, 1000) / 1000f
        val width = virtualizerStrength.coerceIn(0, 1000) / 1000f
        val low = if (bassEnabled) 14f * bass else 0f
        val lowMid = if (bassEnabled) 7f * bass else 0f
        val stereo = if (virtualizerEnabled) 6f * width else 0f
        val inverseStereo = if (stereo == 0f) 0f else -stereo
        return StereoPreEqPlan(
            leftGainsDb = floatArrayOf(low, lowMid, stereo, inverseStereo, 0f),
            rightGainsDb = floatArrayOf(low, lowMid, inverseStereo, stereo, 0f),
        )
    }
}
