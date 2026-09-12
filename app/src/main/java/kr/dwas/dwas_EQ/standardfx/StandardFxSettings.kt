package kr.dwas.dwas_EQ.standardfx

import kr.dwas.dwas_EQ.domain.EqCurve
import kotlin.math.round

enum class ReverbPreset { NONE, SMALL_ROOM, MEDIUM_ROOM, LARGE_ROOM, MEDIUM_HALL, LARGE_HALL, PLATE }

data class LimiterSettings(
    val enabled: Boolean = false,
    val attackMs: Float = 1f,
    val releaseMs: Float = 60f,
    val ratio: Float = 10f,
    val thresholdDb: Float = -2f,
    val postGainDb: Float = 0f,
) {
    fun normalized(): LimiterSettings = copy(
        attackMs = attackMs.coerceIn(1f, 200f),
        releaseMs = releaseMs.coerceIn(10f, 1000f),
        ratio = ratio.coerceIn(1f, 20f),
        thresholdDb = thresholdDb.coerceIn(-24f, 0f),
        postGainDb = postGainDb.coerceIn(-12f, 12f),
    )
}

data class StandardFxSettings(
    val attenuatorEnabled: Boolean = false,
    val automaticAttenuation: Boolean = true,
    val manualAttenuationDb: Float = 0f,
    val channelBalanceEnabled: Boolean = false,
    val leftBalanceDb: Float = 0f,
    val rightBalanceDb: Float = 0f,
    val limiter: LimiterSettings = LimiterSettings(),
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0,
    val reverbEnabled: Boolean = false,
    val reverbPreset: ReverbPreset = ReverbPreset.NONE,
) {
    fun normalized(): StandardFxSettings = copy(
        manualAttenuationDb = quantizeTenth(manualAttenuationDb.coerceIn(-12f, 0f)),
        leftBalanceDb = quantizeTenth(leftBalanceDb.coerceIn(-10f, 0f)),
        rightBalanceDb = quantizeTenth(rightBalanceDb.coerceIn(-10f, 0f)),
        limiter = limiter.normalized(),
        bassBoostStrength = bassBoostStrength.coerceIn(0, 1000),
        virtualizerStrength = virtualizerStrength.coerceIn(0, 1000),
    )

    fun withBassBoostEnabled(enabled: Boolean): StandardFxSettings = copy(
        bassBoostEnabled = enabled,
        bassBoostStrength = bassBoostStrength,
    )

    fun withVirtualizerEnabled(enabled: Boolean): StandardFxSettings = copy(
        virtualizerEnabled = enabled,
        virtualizerStrength = virtualizerStrength,
    )

    fun withReverbEnabled(enabled: Boolean): StandardFxSettings = copy(
        reverbEnabled = enabled,
        reverbPreset = ReverbPreset.NONE,
    )

    val hasAnyEnabledEffect: Boolean
        get() = attenuatorEnabled || channelBalanceEnabled || limiter.enabled ||
            (bassBoostEnabled && bassBoostStrength > 0) || (virtualizerEnabled && virtualizerStrength > 0) ||
            (reverbEnabled && reverbPreset != ReverbPreset.NONE)

    val requiresRuntimeHold: Boolean
        get() = attenuatorEnabled || channelBalanceEnabled || limiter.enabled ||
            bassBoostEnabled || virtualizerEnabled ||
            (reverbEnabled && reverbPreset != ReverbPreset.NONE)

    companion object {
        fun quantizeTenth(value: Float): Float = round(value * 10f) / 10f
    }
}



object SoundEffectsMasterPolicy {
    fun shouldRun(masterEnabled: Boolean, settings: StandardFxSettings): Boolean =
        masterEnabled && settings.requiresRuntimeHold

    const val preserveFxWhenEqualizerChanges: Boolean = true
}

data class StandardFxCapabilities(
    val dynamicsProcessing: Boolean = false,
    val bassBoost: Boolean = false,
    val virtualizer: Boolean = false,
    val presetReverb: Boolean = false,
    val details: Map<String, String> = emptyMap(),
)

object HeadroomCalculator {
    fun appliedCurve(eqTransactionActive: Boolean, lastAppliedCurve: EqCurve): EqCurve =
        if (eqTransactionActive) lastAppliedCurve else EqCurve.flat()

    fun attenuationDb(settings: StandardFxSettings, lastAppliedCurve: EqCurve): Float {
        if (!settings.attenuatorEnabled) return 0f
        val requested = if (settings.automaticAttenuation) {
            -lastAppliedCurve.maxPositiveGainDb()
        } else {
            settings.manualAttenuationDb
        }
        return StandardFxSettings.quantizeTenth(requested.coerceIn(-12f, 0f))
    }

    fun channelInputGainsDb(settings: StandardFxSettings, attenuationDb: Float): Pair<Float, Float> {
        val leftBalance = if (settings.channelBalanceEnabled) settings.leftBalanceDb else 0f
        val rightBalance = if (settings.channelBalanceEnabled) settings.rightBalanceDb else 0f
        return StandardFxSettings.quantizeTenth((attenuationDb + leftBalance).coerceIn(-24f, 12f)) to
            StandardFxSettings.quantizeTenth((attenuationDb + rightBalance).coerceIn(-24f, 12f))
    }

    fun dynamicsChannelInputGainsDb(settings: StandardFxSettings, attenuationDb: Float): Pair<Float, Float> {
        val (left, right) = channelInputGainsDb(settings, attenuationDb)
        return right to left
    }
}

object StandardFxCodec {
    fun encode(settings: StandardFxSettings): String {
        val s = settings.normalized()
        return listOf(
            "v=1",
            "ae=${s.attenuatorEnabled}",
            "aa=${s.automaticAttenuation}",
            "ma=${s.manualAttenuationDb}",
            "ce=${s.channelBalanceEnabled}",
            "lb=${s.leftBalanceDb}",
            "rb=${s.rightBalanceDb}",
            "le=${s.limiter.enabled}",
            "la=${s.limiter.attackMs}",
            "lr=${s.limiter.releaseMs}",
            "lra=${s.limiter.ratio}",
            "lt=${s.limiter.thresholdDb}",
            "lp=${s.limiter.postGainDb}",
            "be=${s.bassBoostEnabled}",
            "bs=${s.bassBoostStrength}",
            "ve=${s.virtualizerEnabled}",
            "vs=${s.virtualizerStrength}",
            "re=${s.reverbEnabled}",
            "rp=${s.reverbPreset.name}",
        ).joinToString(";")
    }

    fun decode(raw: String?): StandardFxSettings {
        if (raw.isNullOrBlank()) return StandardFxSettings()
        val map = raw.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
        }.toMap()
        if (map["v"] != "1") return StandardFxSettings()
        return runCatching {
            StandardFxSettings(
                attenuatorEnabled = map.bool("ae"),
                automaticAttenuation = map.bool("aa", true),
                manualAttenuationDb = map.float("ma"),
                channelBalanceEnabled = map.bool("ce"),
                leftBalanceDb = map.float("lb"),
                rightBalanceDb = map.float("rb"),
                limiter = LimiterSettings(
                    enabled = map.bool("le"),
                    attackMs = map.float("la", 1f),
                    releaseMs = map.float("lr", 60f),
                    ratio = map.float("lra", 10f),
                    thresholdDb = map.float("lt", -2f),
                    postGainDb = map.float("lp"),
                ),
                bassBoostEnabled = map.bool("be"),
                bassBoostStrength = map.int("bs"),
                virtualizerEnabled = map.bool("ve"),
                virtualizerStrength = map.int("vs"),
                reverbEnabled = map.bool("re"),
                reverbPreset = runCatching { ReverbPreset.valueOf(map["rp"] ?: ReverbPreset.NONE.name) }.getOrDefault(ReverbPreset.NONE),
            ).normalized()
        }.getOrDefault(StandardFxSettings())
    }

    private fun Map<String, String>.bool(key: String, default: Boolean = false): Boolean = this[key]?.toBooleanStrictOrNull() ?: default
    private fun Map<String, String>.float(key: String, default: Float = 0f): Float = this[key]?.toFloatOrNull() ?: default
    private fun Map<String, String>.int(key: String, default: Int = 0): Int = this[key]?.toIntOrNull() ?: default
}
