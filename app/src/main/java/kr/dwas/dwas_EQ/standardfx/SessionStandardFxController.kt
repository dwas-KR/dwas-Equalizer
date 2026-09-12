package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.PresetReverb
import kr.dwas.dwas_EQ.domain.EqCurve

class SessionStandardFxController(
    val audioSession: Int,
    private val priority: Int = STANDARD_PRIORITY,
) : AutoCloseable {
    init { require(audioSession > 0) { "Session FX requires a non-zero audio session" } }

    private var bassBoost: StableBassBoostEffect? = null
    private var virtualizer: StableVirtualizerEffect? = null
    private var insertPresetReverb: InsertPresetReverbEffect? = null
    private var insertEnvironmentalReverb: InsertEnvironmentalReverbEffect? = null
    private val dynamicsController = StandardFxController(audioSession, priority)
    private val unifiedEqCoreController = PeridotUnifiedEqCoreController(audioSession, priority)
    private var activeReverbPreset: ReverbPreset = ReverbPreset.NONE
    private var activeBassBoostStrength: Int = -1
    private var activeVirtualizerStrength: Int = -1
    private var virtualizerSpeakerModePrimed: Boolean = false

    @Synchronized
    fun apply(
        settings: StandardFxSettings,
        fullDynamicsRouting: Boolean = false,
        forceVirtualizerMode: Boolean = false,
        environmentalReverbRouting: Boolean = false,
        allowVendorProxyStrengthEffects: Boolean = false,
        preferDirectSoftwareStrengthEffects: Boolean = false,
        unifiedEqCoreRouting: Boolean = false,
        unifiedEqCurve: EqCurve = EqCurve.flat(),
        unifiedBassStrength: Int = 0,
    ): StandardFxApplyResult {
        val normalized = settings.normalized()
        val active = linkedSetOf<String>()
        val failures = linkedMapOf<String, String>()
        if (fullDynamicsRouting) {
            unifiedEqCoreController.close()
            applyFullDynamics(normalized, active, failures)
        } else if (unifiedEqCoreRouting) {
            dynamicsController.close()
            releaseBassBoost()
            val result = unifiedEqCoreController.apply(normalized, unifiedEqCurve, unifiedBassStrength)
            active += result.activeEffects
            failures += result.failedEffects
            applyVirtualizer(normalized, active, failures, forceVirtualizerMode, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)
        } else {
            unifiedEqCoreController.close()
            applyCoreDynamics(normalized, active, failures)
            applyBassBoost(normalized, active, failures, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)
            applyVirtualizer(normalized, active, failures, forceVirtualizerMode, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)
        }
        if (environmentalReverbRouting) applyEnvironmentalReverb(normalized, active, failures) else applyPresetReverb(normalized, active, failures)
        return StandardFxApplyResult(normalized, active, failures)
    }

    private fun applyFullDynamics(
        settings: StandardFxSettings,
        active: MutableSet<String>,
        failures: MutableMap<String, String>,
    ) {
        releaseBassBoost()
        releaseVirtualizer()
        val dynamicsSettings = settings.copy(reverbEnabled = false, reverbPreset = ReverbPreset.NONE)
        val result = dynamicsController.apply(
            settings = dynamicsSettings,
            lastAppliedCurve = EqCurve.flat(),
            nativeBass = settings.bassBoostEnabled,
            nativeVirtualizer = settings.virtualizerEnabled,
        )
        active += result.activeEffects
        failures += result.failedEffects
    }

    private fun applyCoreDynamics(
        settings: StandardFxSettings,
        active: MutableSet<String>,
        failures: MutableMap<String, String>,
    ) {
        val needsCoreDynamics = settings.attenuatorEnabled || settings.channelBalanceEnabled || settings.limiter.enabled
        if (!needsCoreDynamics) {
            dynamicsController.close()
            return
        }
        val coreSettings = settings.copy(
            bassBoostEnabled = false,
            bassBoostStrength = 0,
            virtualizerEnabled = false,
            virtualizerStrength = 0,
            reverbEnabled = false,
            reverbPreset = ReverbPreset.NONE,
        )
        val result = dynamicsController.apply(
            settings = coreSettings,
            lastAppliedCurve = EqCurve.flat(),
            nativeBass = false,
            nativeVirtualizer = false,
            omitNeutralPreEq = true,
        )
        active += result.activeEffects
        failures += result.failedEffects
    }

    private fun applyBassBoost(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>, allowVendorProxyStrengthEffects: Boolean, preferDirectSoftwareStrengthEffects: Boolean) {
        if (!settings.bassBoostEnabled) { releaseBassBoost(); return }
        runCatching {
            val effect = bassBoost ?: StableBassBoostEffect.open(audioSession, priority, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects).also { bassBoost = it }
            check(effect.hasControl) { "hasControl=false" }
            activeBassBoostStrength = primeNeutralIfNeeded(effect, activeBassBoostStrength, settings.bassBoostStrength)
            if (effect.strengthSupported && activeBassBoostStrength != settings.bassBoostStrength) {
                effect.setStrength(settings.bassBoostStrength)
                activeBassBoostStrength = settings.bassBoostStrength
            }
            if (!effect.strengthSupported && settings.bassBoostStrength > 0) effect.enabled = true
            if (effect.strengthSupported) effect.enabled = true
            if (settings.bassBoostStrength > 0) active += "Bass Boost"
        }.onFailure { error ->
            failures["Bass Boost"] = error.shortMessage()
            releaseBassBoost()
        }
    }

    private fun applyVirtualizer(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>, forceVirtualizerMode: Boolean, allowVendorProxyStrengthEffects: Boolean, preferDirectSoftwareStrengthEffects: Boolean) {
        if (!settings.virtualizerEnabled) { releaseVirtualizer(); return }
        runCatching {
            val newlyOpened = virtualizer == null
            val effect = virtualizer ?: StableVirtualizerEffect.open(audioSession, priority, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects).also { virtualizer = it }
            check(effect.hasControl) { "hasControl=false" }
            activeVirtualizerStrength = primeNeutralIfNeeded(effect, activeVirtualizerStrength, settings.virtualizerStrength)
            if (SessionFxStabilityPolicy.shouldWarmNewSpeakerVirtualizer(newlyOpened, forceVirtualizerMode, settings.virtualizerStrength)) {
                Thread.sleep(SessionFxStabilityPolicy.virtualizerActivationWarmupMs)
            }
            if (effect.strengthSupported && activeVirtualizerStrength != settings.virtualizerStrength) {
                effect.setStrength(settings.virtualizerStrength)
                activeVirtualizerStrength = settings.virtualizerStrength
            }
            if (!effect.strengthSupported && settings.virtualizerStrength > 0) effect.enabled = true
            if (effect.strengthSupported) effect.enabled = true
            if (!forceVirtualizerMode) virtualizerSpeakerModePrimed = false
            if (SessionFxStabilityPolicy.shouldForceSpeakerVirtualizationMode(
                    speakerSafeRouting = forceVirtualizerMode,
                    requestedStrength = settings.virtualizerStrength,
                    speakerModePrimed = virtualizerSpeakerModePrimed,
                )
            ) {
                check(effect.forceSpeakerCompatibleMode()) { "speaker-compatible virtualization mode unavailable" }
                check(effect.virtualizationMode() != 0) { "virtualization mode readback=OFF" }
                virtualizerSpeakerModePrimed = true
            }
            if (settings.virtualizerStrength > 0) active += "Virtualizer"
        }.onFailure { error ->
            failures["Virtualizer"] = error.shortMessage()
            releaseVirtualizer()
        }
    }

    private fun primeNeutralIfNeeded(effect: StableBassBoostEffect, currentStrength: Int, requestedStrength: Int): Int {
        if (!SessionFxStabilityPolicy.shouldPrimeNeutral(effect.strengthSupported, currentStrength)) {
            return if (SessionFxStabilityPolicy.shouldEnableFixedStrength(effect.strengthSupported, requestedStrength)) requestedStrength else currentStrength
        }
        effect.setStrength(0)
        effect.enabled = true
        return 0
    }

    private fun primeNeutralIfNeeded(effect: StableVirtualizerEffect, currentStrength: Int, requestedStrength: Int): Int {
        if (!SessionFxStabilityPolicy.shouldPrimeNeutral(effect.strengthSupported, currentStrength)) {
            return if (SessionFxStabilityPolicy.shouldEnableFixedStrength(effect.strengthSupported, requestedStrength)) requestedStrength else currentStrength
        }
        effect.setStrength(0)
        effect.enabled = true
        return 0
    }

    private fun applyPresetReverb(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>) {
        if (insertEnvironmentalReverb != null) releaseEnvironmentalReverb()
        if (!settings.reverbEnabled || settings.reverbPreset == ReverbPreset.NONE) { releasePresetReverb(); return }
        if (activeReverbPreset != settings.reverbPreset) releasePresetReverb()
        val requested = settings.reverbPreset.toAndroidPreset()
        runCatching {
            val effect = insertPresetReverb ?: InsertPresetReverbEffect.open(audioSession, priority).also { insertPresetReverb = it }
            check(effect.hasControl) { "insert hasControl=false" }
            effect.enabled = false
            effect.setPresetAndVerify(requested)
            effect.enabled = true
            check(effect.enabled) { "insert enabled readback=false" }
            effect.verifyPresetAfterEnable(requested)
            activeReverbPreset = settings.reverbPreset
            active += "Preset Reverb"
        }.onFailure { error ->
            failures["Preset Reverb"] = "insert unavailable: ${error.shortMessage()}"
            releasePresetReverb()
        }
    }

    private fun applyEnvironmentalReverb(settings: StandardFxSettings, active: MutableSet<String>, failures: MutableMap<String, String>) {
        if (insertPresetReverb != null) releasePresetReverb()
        if (!settings.reverbEnabled || settings.reverbPreset == ReverbPreset.NONE) { releaseEnvironmentalReverb(); return }
        val current = insertEnvironmentalReverb
        val reuseEnvironmentalReverb = current != null && SessionFxStabilityPolicy.shouldReuseEnvironmentalReverb(
            hasControl = current.hasControl,
            enabled = current.enabled,
            activePreset = activeReverbPreset,
            requestedPreset = settings.reverbPreset,
        )
        if (reuseEnvironmentalReverb) {
            active += "Preset Reverb"
            return
        }
        if (activeReverbPreset != settings.reverbPreset) releaseEnvironmentalReverb()
        val requested = DwasEnvironmentalReverbPresetPolicy.settings(settings.reverbPreset)
        runCatching {
            val effect = insertEnvironmentalReverb ?: InsertEnvironmentalReverbEffect.open(audioSession, priority).also { insertEnvironmentalReverb = it }
            check(effect.hasControl) { "environmental insert hasControl=false" }
            effect.enabled = false
            effect.setSettingsAndVerify(requested)
            effect.enabled = true
            check(effect.enabled) { "environmental insert enabled readback=false" }
            effect.verifySettingsAfterEnable(requested)
            activeReverbPreset = settings.reverbPreset
            active += "Preset Reverb"
        }.onFailure { error ->
            failures["Preset Reverb"] = "environmental insert unavailable: ${error.shortMessage()}"
            releaseEnvironmentalReverb()
        }
    }

    @Synchronized override fun close() {
        unifiedEqCoreController.close()
        dynamicsController.close()
        releaseBassBoost()
        releaseVirtualizer()
        releasePresetReverb()
        releaseEnvironmentalReverb()
    }

    private fun releaseBassBoost() { bassBoost?.let { runCatching { it.close() } }; bassBoost = null; activeBassBoostStrength = -1 }
    private fun releaseVirtualizer() {
        virtualizer?.let { runCatching { it.close() } }
        virtualizer = null
        activeVirtualizerStrength = -1
        virtualizerSpeakerModePrimed = false
    }
    private fun releasePresetReverb() {
        insertPresetReverb?.close(); insertPresetReverb = null
        activeReverbPreset = ReverbPreset.NONE
    }
    private fun releaseEnvironmentalReverb() {
        insertEnvironmentalReverb?.close(); insertEnvironmentalReverb = null
        activeReverbPreset = ReverbPreset.NONE
    }

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

    companion object { private const val STANDARD_PRIORITY = Int.MAX_VALUE }
}
