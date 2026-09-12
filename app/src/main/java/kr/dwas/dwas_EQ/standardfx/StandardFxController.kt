package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import kr.dwas.dwas_EQ.domain.EqCurve

data class StandardFxApplyResult(
    val requested: StandardFxSettings,
    val activeEffects: Set<String>,
    val failedEffects: Map<String, String>,
) {
    val ok: Boolean get() = failedEffects.isEmpty()
    val message: String get() = buildString {
        if (activeEffects.isEmpty()) append("No standard effect is active")
        else append("Active: ").append(activeEffects.joinToString())
        if (failedEffects.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append("Unavailable: ")
            append(failedEffects.entries.joinToString { "${it.key} (${it.value})" })
        }
    }
}

class StandardFxController(
    private val audioSession: Int = GLOBAL_OUTPUT_MIX_SESSION,
    private val priority: Int = STANDARD_PRIORITY,
) : AutoCloseable {
    private var dynamics: DynamicsProcessing? = null
    private var dynamicsPreEqEnabled: Boolean? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var presetReverb: PresetReverb? = null

    @Synchronized
    fun probeCapabilities(
        nativeBass: Boolean = false,
        nativeVirtualizer: Boolean = false,
    ): StandardFxCapabilities {
        val details = linkedMapOf<String, String>()
        val dynamicsOk = probeAndRelease(
            name = "DynamicsProcessing",
            details = details,
            creator = { DynamicsProcessing(priority, audioSession, neutralDynamicsConfig()) },
        )
        val bassOk = if (nativeBass) {
            details["BassBoost routing"] = "dwas_EQ DynamicsProcessing"
            dynamicsOk
        } else {
            probeAndRelease(
                name = "BassBoost",
                details = details,
                creator = { BassBoost(priority, audioSession) },
            ) { effect ->
                details["BassBoost strength"] = if (effect.strengthSupported) "variable 0..1000" else "fixed by implementation"
            }
        }
        val virtualizerOk = if (nativeVirtualizer) {
            details["Virtualizer routing"] = "dwas_EQ DynamicsProcessing"
            dynamicsOk
        } else {
            probeAndRelease(
                name = "Virtualizer",
                details = details,
                creator = { Virtualizer(priority, audioSession) },
            )
        }
        val reverbOk = probeAndRelease(
            name = "PresetReverb",
            details = details,
            creator = { PresetReverb(priority, audioSession) },
        )
        return StandardFxCapabilities(dynamicsOk, bassOk, virtualizerOk, reverbOk, details)
    }

    @Synchronized
    fun apply(
        settings: StandardFxSettings,
        lastAppliedCurve: EqCurve,
        nativeBass: Boolean = false,
        nativeVirtualizer: Boolean = false,
        omitNeutralPreEq: Boolean = false,
    ): StandardFxApplyResult {
        val normalized = settings.normalized()
        val active = linkedSetOf<String>()
        val failures = linkedMapOf<String, String>()

        if (nativeBass) releaseBassBoost()
        if (nativeVirtualizer) releaseVirtualizer()
        applyDynamics(normalized, lastAppliedCurve, nativeBass, nativeVirtualizer, omitNeutralPreEq, active, failures)
        if (!nativeBass) applyBassBoost(normalized, active, failures)
        if (!nativeVirtualizer) applyVirtualizer(normalized, active, failures)
        applyPresetReverb(normalized, active, failures)

        return StandardFxApplyResult(normalized, active, failures)
    }

    private fun applyDynamics(
        settings: StandardFxSettings,
        curve: EqCurve,
        nativeBass: Boolean,
        nativeVirtualizer: Boolean,
        omitNeutralPreEq: Boolean,
        active: MutableSet<String>,
        failures: MutableMap<String, String>,
    ) {
        val needsDynamics = settings.attenuatorEnabled || settings.channelBalanceEnabled || settings.limiter.enabled || nativeBass || nativeVirtualizer
        if (!needsDynamics) {
            releaseDynamics()
            return
        }
        runCatching {
            val attenuation = HeadroomCalculator.attenuationDb(settings, curve)
            val (left, right) = HeadroomCalculator.dynamicsChannelInputGainsDb(settings, attenuation)
            val plan = DwasNativeFxPolicy.stereoPreEqPlan(
                bassEnabled = nativeBass && settings.bassBoostEnabled,
                bassStrength = settings.bassBoostStrength,
                virtualizerEnabled = nativeVirtualizer && settings.virtualizerEnabled,
                virtualizerStrength = settings.virtualizerStrength,
            )
            val preEqEnabled = DynamicsPreEqTopologyPolicy.enabled(nativeBass, nativeVirtualizer, omitNeutralPreEq)
            val preEqBandCount = DynamicsPreEqTopologyPolicy.bandCount(nativeBass, nativeVirtualizer, omitNeutralPreEq)
            if (dynamics != null && dynamicsPreEqEnabled != preEqEnabled) releaseDynamics()
            val effect = dynamics ?: DynamicsProcessing(
                priority,
                audioSession,
                dynamicsConfig(settings, left, right, plan, preEqEnabled, preEqBandCount),
            ).also {
                dynamics = it
                dynamicsPreEqEnabled = preEqEnabled
            }
            check(effect.hasControl()) { "hasControl=false" }
            effect.setInputGainbyChannel(0, left)
            effect.setInputGainbyChannel(1, right)
            effect.setLimiterAllChannelsTo(limiter(settings.limiter))
            if (preEqEnabled) applyNativePreEq(effect, plan)
            effect.enabled = true
            check(effect.enabled) { "enabled readback=false" }
            if (settings.attenuatorEnabled) active += "Attenuator"
            if (settings.channelBalanceEnabled) active += "Channel Balance"
            if (settings.limiter.enabled) active += "Limiter"
            if (nativeBass && settings.bassBoostEnabled && settings.bassBoostStrength > 0) active += "Bass Boost"
            if (nativeVirtualizer && settings.virtualizerEnabled && settings.virtualizerStrength > 0) active += "Virtualizer"
        }.onFailure { error ->
            val reason = error.shortMessage()
            if (settings.attenuatorEnabled || settings.channelBalanceEnabled || settings.limiter.enabled) failures["DynamicsProcessing"] = reason
            if (nativeBass && settings.bassBoostEnabled) failures["Bass Boost"] = reason
            if (nativeVirtualizer && settings.virtualizerEnabled) failures["Virtualizer"] = reason
            releaseDynamics()
        }
    }

    private fun applyBassBoost(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>) {
        if (!settings.bassBoostEnabled) {
            releaseBassBoost()
            return
        }
        runCatching {
            val effect = bassBoost ?: BassBoost(priority, audioSession).also { bassBoost = it }
            check(effect.hasControl()) { "hasControl=false" }
            if (effect.strengthSupported) {
                effect.setStrength(settings.bassBoostStrength.toShort())
            } else if (settings.bassBoostStrength <= 0) {
                releaseBassBoost()
                return@runCatching
            }
            effect.enabled = true
            if (settings.bassBoostStrength > 0) active += "Bass Boost"
        }.onFailure { error ->
            failures["Bass Boost"] = error.shortMessage()
        }
    }

    private fun applyVirtualizer(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>) {
        if (!settings.virtualizerEnabled) {
            releaseVirtualizer()
            return
        }
        runCatching {
            val effect = virtualizer ?: Virtualizer(priority, audioSession).also { virtualizer = it }
            check(effect.hasControl()) { "hasControl=false" }
            if (effect.strengthSupported) {
                effect.setStrength(settings.virtualizerStrength.toShort())
            } else if (settings.virtualizerStrength <= 0) {
                releaseVirtualizer()
                return@runCatching
            }
            effect.enabled = true
            if (settings.virtualizerStrength > 0) active += "Virtualizer"
        }.onFailure { error ->
            failures["Virtualizer"] = error.shortMessage()
        }
    }

    private fun applyPresetReverb(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>) {
        if (!settings.reverbEnabled || settings.reverbPreset == ReverbPreset.NONE) {
            releasePresetReverb()
            return
        }
        runCatching {
            val effect = presetReverb ?: PresetReverb(priority, audioSession).also { presetReverb = it }
            check(effect.hasControl()) { "hasControl=false" }
            val requestedPreset = settings.reverbPreset.toAndroidPreset()
            effect.preset = requestedPreset
            effect.enabled = true
            check(effect.enabled) { "enabled readback=false" }
            check(effect.preset == requestedPreset) { "preset readback mismatch" }
            active += "Preset Reverb (experimental)"
        }.onFailure { error ->
            failures["Preset Reverb"] = error.shortMessage()
            releasePresetReverb()
        }
    }

    private fun dynamicsConfig(
        settings: StandardFxSettings,
        left: Float,
        right: Float,
        plan: DwasNativeFxPolicy.StereoPreEqPlan,
        preEqEnabled: Boolean,
        preEqBandCount: Int,
    ): DynamicsProcessing.Config {
        val builder = DynamicsProcessing.Config.Builder(
            VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNEL_COUNT,
            preEqEnabled,
            preEqBandCount,
            false,
            0,
            false,
            0,
            true,
        )
            .setInputGainByChannelIndex(0, left)
            .setInputGainByChannelIndex(1, right)
            .setLimiterAllChannelsTo(limiter(settings.limiter))
        if (preEqEnabled) {
            builder.setPreEqByChannelIndex(0, preEq(plan.leftGainsDb))
            builder.setPreEqByChannelIndex(1, preEq(plan.rightGainsDb))
        }
        return builder.build()
    }

    private fun neutralDynamicsConfig(): DynamicsProcessing.Config = dynamicsConfig(
        StandardFxSettings(),
        0f,
        0f,
        DwasNativeFxPolicy.stereoPreEqPlan(false, 0, false, 0),
        true,
        DwasNativeFxPolicy.PRE_EQ_BAND_COUNT,
    )

    private fun preEq(gains: FloatArray): DynamicsProcessing.Eq {
        val eq = DynamicsProcessing.Eq(true, true, DwasNativeFxPolicy.PRE_EQ_BAND_COUNT)
        for (band in 0 until DwasNativeFxPolicy.PRE_EQ_BAND_COUNT) {
            eq.setBand(
                band,
                DynamicsProcessing.EqBand(
                    true,
                    DwasNativeFxPolicy.PRE_EQ_CUTOFFS_HZ[band],
                    gains[band],
                ),
            )
        }
        return eq
    }

    private fun applyNativePreEq(effect: DynamicsProcessing, plan: DwasNativeFxPolicy.StereoPreEqPlan) {
        for (band in 0 until DwasNativeFxPolicy.PRE_EQ_BAND_COUNT) {
            val cutoff = DwasNativeFxPolicy.PRE_EQ_CUTOFFS_HZ[band]
            effect.setPreEqBandByChannelIndex(0, band, DynamicsProcessing.EqBand(true, cutoff, plan.leftGainsDb[band]))
            effect.setPreEqBandByChannelIndex(1, band, DynamicsProcessing.EqBand(true, cutoff, plan.rightGainsDb[band]))
        }
    }

    private fun limiter(settings: LimiterSettings): DynamicsProcessing.Limiter {
        val normalized = settings.normalized()
        return DynamicsProcessing.Limiter(
            true,
            normalized.enabled,
            0,
            normalized.attackMs,
            normalized.releaseMs,
            normalized.ratio,
            normalized.thresholdDb,
            normalized.postGainDb,
        )
    }

    private fun <T : AudioEffect> probeAndRelease(
        name: String,
        details: MutableMap<String, String>,
        creator: () -> T,
        inspect: (T) -> Unit = {},
    ): Boolean = probe(name, details) {
        val effect = creator()
        try {
            check(effect.hasControl()) { "hasControl=false" }
            inspect(effect)
        } finally {
            runCatching { effect.release() }
        }
    }

    private inline fun probe(name: String, details: MutableMap<String, String>, block: () -> Unit): Boolean =
        runCatching(block).fold(
            onSuccess = { details[name] = "available/control=true"; true },
            onFailure = { details[name] = it.shortMessage(); false },
        )

    @Synchronized
    override fun close() {
        releaseDynamics()
        releaseBassBoost()
        releaseVirtualizer()
        releasePresetReverb()
    }

    private fun releaseDynamics() { dynamics?.let { runCatching { it.release() } }; dynamics = null; dynamicsPreEqEnabled = null }
    private fun releaseBassBoost() { bassBoost?.let { runCatching { it.release() } }; bassBoost = null }
    private fun releaseVirtualizer() { virtualizer?.let { runCatching { it.release() } }; virtualizer = null }
    private fun releasePresetReverb() { presetReverb?.let { runCatching { it.release() } }; presetReverb = null }

    private fun ReverbPreset.toAndroidPreset(): Short = when (this) {
        ReverbPreset.NONE -> PresetReverb.PRESET_NONE
        ReverbPreset.SMALL_ROOM -> PresetReverb.PRESET_SMALLROOM
        ReverbPreset.MEDIUM_ROOM -> PresetReverb.PRESET_MEDIUMROOM
        ReverbPreset.LARGE_ROOM -> PresetReverb.PRESET_LARGEROOM
        ReverbPreset.MEDIUM_HALL -> PresetReverb.PRESET_MEDIUMHALL
        ReverbPreset.LARGE_HALL -> PresetReverb.PRESET_LARGEHALL
        ReverbPreset.PLATE -> PresetReverb.PRESET_PLATE
    }

    private fun Throwable.shortMessage(): String = "${this::class.simpleName}: ${message ?: "unsupported"}"

    companion object {
        private const val GLOBAL_OUTPUT_MIX_SESSION = 0
        private const val STANDARD_PRIORITY = 1000
        private const val CHANNEL_COUNT = 2
        private const val VARIANT_FAVOR_FREQUENCY_RESOLUTION = 0
    }
}
