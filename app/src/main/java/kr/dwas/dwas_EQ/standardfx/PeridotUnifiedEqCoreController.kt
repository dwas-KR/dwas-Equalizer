package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.DynamicsProcessing
import kr.dwas.dwas_EQ.domain.EqCurve

class PeridotUnifiedEqCoreController(
    val audioSession: Int,
    private val priority: Int = STANDARD_PRIORITY,
) : AutoCloseable {
    init { require(audioSession > 0) { "Unified EQ core requires a non-zero audio session" } }

    private var dynamics: DynamicsProcessing? = null

    @Synchronized
    fun apply(
        settings: StandardFxSettings,
        curve: EqCurve,
        bassStrength: Int,
    ): StandardFxApplyResult {
        val normalized = settings.normalized()
        val targetBassStrength = bassStrength.coerceIn(0, 1000)
        val active = linkedSetOf<String>()
        val failures = linkedMapOf<String, String>()
        val coreRequested = normalized.attenuatorEnabled || normalized.channelBalanceEnabled || normalized.limiter.enabled
        if (!coreRequested) {
            close()
            return StandardFxApplyResult(normalized, active, failures)
        }
        runCatching {
            val attenuation = HeadroomCalculator.attenuationDb(normalized, curve)
            val (left, right) = HeadroomCalculator.dynamicsChannelInputGainsDb(normalized, attenuation)
            val gains = PeridotUnifiedEqCorePolicy.combinedBandGains(curve, targetBassStrength)
            val effect = dynamics ?: DynamicsProcessing(
                priority,
                audioSession,
                dynamicsConfig(normalized, left, right, gains),
            ).also { dynamics = it }
            check(effect.hasControl()) { "hasControl=false" }
            effect.setInputGainbyChannel(0, left)
            effect.setInputGainbyChannel(1, right)
            effect.setLimiterAllChannelsTo(limiter(normalized.limiter))
            applyPreEq(effect, gains)
            effect.enabled = true
            check(effect.enabled) { "enabled readback=false" }
            if (normalized.attenuatorEnabled) active += "Attenuator"
            if (normalized.channelBalanceEnabled) active += "Channel Balance"
            if (normalized.limiter.enabled) active += "Limiter"
            if (targetBassStrength > 0) active += "Bass Boost"
        }.onFailure { error ->
            val reason = "${error::class.simpleName}: ${error.message ?: "unsupported"}"
            if (normalized.attenuatorEnabled) failures["Attenuator"] = reason
            if (normalized.channelBalanceEnabled) failures["Channel Balance"] = reason
            if (normalized.limiter.enabled) failures["Limiter"] = reason
            if (targetBassStrength > 0) failures["Bass Boost"] = reason
            close()
        }
        return StandardFxApplyResult(normalized, active, failures)
    }

    private fun dynamicsConfig(
        settings: StandardFxSettings,
        left: Float,
        right: Float,
        gains: FloatArray,
    ): DynamicsProcessing.Config = DynamicsProcessing.Config.Builder(
        VARIANT_FAVOR_FREQUENCY_RESOLUTION,
        CHANNEL_COUNT,
        true,
        PeridotUnifiedEqCorePolicy.CUTOFF_FREQUENCIES_HZ.size,
        false,
        0,
        false,
        0,
        true,
    )
        .setInputGainByChannelIndex(0, left)
        .setInputGainByChannelIndex(1, right)
        .setPreEqByChannelIndex(0, preEq(gains))
        .setPreEqByChannelIndex(1, preEq(gains))
        .setLimiterAllChannelsTo(limiter(settings.limiter))
        .build()

    private fun preEq(gains: FloatArray): DynamicsProcessing.Eq {
        val count = PeridotUnifiedEqCorePolicy.CUTOFF_FREQUENCIES_HZ.size
        val eq = DynamicsProcessing.Eq(true, true, count)
        for (band in 0 until count) {
            eq.setBand(
                band,
                DynamicsProcessing.EqBand(
                    true,
                    PeridotUnifiedEqCorePolicy.CUTOFF_FREQUENCIES_HZ[band],
                    gains[band],
                ),
            )
        }
        return eq
    }

    private fun applyPreEq(effect: DynamicsProcessing, gains: FloatArray) {
        for (band in PeridotUnifiedEqCorePolicy.CUTOFF_FREQUENCIES_HZ.indices) {
            val value = DynamicsProcessing.EqBand(
                true,
                PeridotUnifiedEqCorePolicy.CUTOFF_FREQUENCIES_HZ[band],
                gains[band],
            )
            effect.setPreEqBandByChannelIndex(0, band, value)
            effect.setPreEqBandByChannelIndex(1, band, value)
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

    @Synchronized
    override fun close() {
        dynamics?.let { runCatching { it.release() } }
        dynamics = null
    }

    companion object {
        private const val CHANNEL_COUNT = 2
        private const val VARIANT_FAVOR_FREQUENCY_RESOLUTION = 0
        private const val STANDARD_PRIORITY = Int.MAX_VALUE
    }
}
