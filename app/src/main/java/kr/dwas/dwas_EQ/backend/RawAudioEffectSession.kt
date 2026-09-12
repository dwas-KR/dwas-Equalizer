package kr.dwas.dwas_EQ.backend

import android.media.audiofx.AudioEffect
import kr.dwas.dwas_EQ.core.DapProtocol
import kr.dwas.dwas_EQ.core.EqSpec
import kr.dwas.dwas_EQ.persistence.PersistentGainSession
import java.lang.reflect.Method
import java.util.UUID

class RawAudioEffectSession private constructor(
    private val effect: AudioEffect,
    private val rawSetParameter: Method,
    private val rawGetParameter: Method,
) : PersistentGainSession {

    override val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)

    override fun readGains(): IntArray {
        val key = DapProtocol.buildReadKey(EqSpec.MUSIC_PROFILE, EqSpec.GEQ_GAINS_PARAMETER)
        val output = ByteArray(EqSpec.DAP_GAIN_COUNT * 4)
        val status = (rawGetParameter.invoke(effect, key, output) as Number).toInt()
        check(status >= 0) { "AudioEffect.getParameter failed: $status" }
        return DapProtocol.decodeGains(output)
    }

    override fun writeGains(gains: IntArray) {
        require(gains.size == EqSpec.DAP_GAIN_COUNT)
        val selector = DapProtocol.intToBytes(EqSpec.AUDIO_EFFECT_DAP_SELECTOR)
        val payload = DapProtocol.buildWritePayload(EqSpec.MUSIC_PROFILE, EqSpec.GEQ_GAINS_PARAMETER, gains)
        val status = (rawSetParameter.invoke(effect, selector, payload) as Number).toInt()
        check(status >= 0) { "AudioEffect.setParameter failed: $status" }
    }

    override fun readGeqEnabled(): Boolean {
        val key = DapProtocol.buildReadKey(EqSpec.MUSIC_PROFILE, EqSpec.GEQ_ENABLE_PARAMETER)
        val output = ByteArray(EqSpec.DAP_SCALAR_READBACK_BYTES)
        val status = (rawGetParameter.invoke(effect, key, output) as Number).toInt()
        check(status >= 0) { "AudioEffect.getParameter failed: $status" }
        return DapProtocol.decodeScalar(output) != 0
    }

    override fun writeGeqEnabled(enabled: Boolean) {
        val selector = DapProtocol.intToBytes(EqSpec.AUDIO_EFFECT_DAP_SELECTOR)
        val payload = DapProtocol.buildScalarWritePayload(
            EqSpec.MUSIC_PROFILE,
            EqSpec.GEQ_ENABLE_PARAMETER,
            if (enabled) 1 else 0,
        )
        val status = (rawSetParameter.invoke(effect, selector, payload) as Number).toInt()
        check(status >= 0) { "AudioEffect.setParameter failed: $status" }
    }

    override fun close() {
        runCatching { effect.release() }
    }

    companion object {
        
        val EFFECT_TYPE_NULL: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
        val DAP_IMPLEMENTATION_UUID: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        const val PRIORITY = 1
        const val GLOBAL_SESSION = 0

        fun queryDapDescriptor(): AudioEffect.Descriptor? =
            runCatching { AudioEffect.queryEffects()?.firstOrNull { it.uuid == DAP_IMPLEMENTATION_UUID } }.getOrNull()

        private fun createHiddenAudioEffect(): AudioEffect {
            
            
            
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            return constructor.newInstance(EFFECT_TYPE_NULL, DAP_IMPLEMENTATION_UUID, PRIORITY, GLOBAL_SESSION) as AudioEffect
        }

        fun open(): RawAudioEffectSession {
            HiddenApiAccess.ensureEnabled().getOrThrow()
            val effect = createHiddenAudioEffect()
            try {
                val set = AudioEffect::class.java.getDeclaredMethod("setParameter", ByteArray::class.java, ByteArray::class.java).apply { isAccessible = true }
                val get = AudioEffect::class.java.getDeclaredMethod("getParameter", ByteArray::class.java, ByteArray::class.java).apply { isAccessible = true }
                return RawAudioEffectSession(effect, set, get)
            } catch (t: Throwable) {
                runCatching { effect.release() }
                throw t
            }
        }
    }
}
