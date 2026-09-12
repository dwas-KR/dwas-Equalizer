package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import kr.dwas.dwas_EQ.backend.HiddenApiAccess
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private interface SessionStrengthEffect : AutoCloseable {
    val hasControl: Boolean
    val strengthSupported: Boolean
    val enabled: Boolean
    val implementation: String
    fun setEnabled(value: Boolean)
    fun setStrength(value: Int)
    fun roundedStrength(): Int
    fun forceSpeakerCompatibleMode(): Boolean = false
    fun virtualizationMode(): Int = 0
}

private class PublicBassBoostEffect(priority: Int, audioSession: Int) : SessionStrengthEffect {
    private val effect = BassBoost(priority, audioSession)
    override val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)
    override val strengthSupported: Boolean get() = effect.strengthSupported
    override val enabled: Boolean get() = runCatching { effect.enabled }.getOrDefault(false)
    override val implementation: String = "android.media.audiofx.BassBoost"
    override fun setEnabled(value: Boolean) {
        val status = effect.setEnabled(value)
        check(status == AudioEffect.SUCCESS) { "Bass Boost setEnabled failed: $status" }
    }
    override fun setStrength(value: Int) { effect.setStrength(value.coerceIn(0, 1000).toShort()) }
    override fun roundedStrength(): Int = effect.roundedStrength.toInt()
    override fun close() { runCatching { effect.release() } }
}

private class PublicVirtualizerEffect(priority: Int, audioSession: Int) : SessionStrengthEffect {
    private val effect = Virtualizer(priority, audioSession)
    override val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)
    override val strengthSupported: Boolean get() = effect.strengthSupported
    override val enabled: Boolean get() = runCatching { effect.enabled }.getOrDefault(false)
    override val implementation: String = "android.media.audiofx.Virtualizer"
    override fun setEnabled(value: Boolean) {
        val status = effect.setEnabled(value)
        check(status == AudioEffect.SUCCESS) { "Virtualizer setEnabled failed: $status" }
    }
    override fun setStrength(value: Int) { effect.setStrength(value.coerceIn(0, 1000).toShort()) }
    override fun roundedStrength(): Int = effect.roundedStrength.toInt()
    @Suppress("DEPRECATION")
    override fun forceSpeakerCompatibleMode(): Boolean {
        if (forceModeAndVerify(Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL)) return true
        return forceModeAndVerify(Virtualizer.VIRTUALIZATION_MODE_BINAURAL)
    }
    @Suppress("DEPRECATION")
    private fun forceModeAndVerify(mode: Int): Boolean {
        if (!runCatching { effect.forceVirtualizationMode(mode) }.getOrDefault(false)) return false
        SessionFxStabilityPolicy.verificationRetryDelaysMs.forEach { delayMs ->
            if (delayMs > 0L) Thread.sleep(delayMs)
            if (virtualizationMode() != Virtualizer.VIRTUALIZATION_MODE_OFF) return true
        }
        return false
    }
    @Suppress("DEPRECATION")
    override fun virtualizationMode(): Int = runCatching { effect.virtualizationMode }.getOrDefault(Virtualizer.VIRTUALIZATION_MODE_OFF)
    override fun close() {
        runCatching { effect.setEnabled(false) }
        runCatching { effect.release() }
    }
}


private class DirectSoftwareStrengthEffect(
    effectType: UUID,
    implementationUuid: UUID,
    priority: Int,
    audioSession: Int,
    private val label: String,
    private val virtualizerMode: Boolean,
) : SessionStrengthEffect {
    private val effect: AudioEffect
    private val rawSetParameter: Method
    private val rawGetParameter: Method

    init {
        HiddenApiAccess.ensureEnabled().getOrThrow()
        val constructor = AudioEffect::class.java.getDeclaredConstructor(
            UUID::class.java,
            UUID::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        effect = constructor.newInstance(effectType, implementationUuid, priority, audioSession) as AudioEffect
        try {
            rawSetParameter = AudioEffect::class.java.getDeclaredMethod("setParameter", ByteArray::class.java, ByteArray::class.java).apply { isAccessible = true }
            rawGetParameter = AudioEffect::class.java.getDeclaredMethod("getParameter", ByteArray::class.java, ByteArray::class.java).apply { isAccessible = true }
        } catch (t: Throwable) {
            runCatching { effect.release() }
            throw t
        }
    }

    override val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)
    override val strengthSupported: Boolean get() = readStrengthSupported()
    override val enabled: Boolean get() = runCatching { effect.enabled }.getOrDefault(false)
    override val implementation: String = "dwas_EQ direct software $label"

    override fun setEnabled(value: Boolean) {
        val status = effect.setEnabled(value)
        check(status == AudioEffect.SUCCESS) { "$label setEnabled failed: $status" }
    }

    override fun setStrength(value: Int) {
        setShortParameter(PARAM_STRENGTH, value.coerceIn(0, 1000).toShort())
    }

    override fun roundedStrength(): Int = getShortParameter(PARAM_STRENGTH).toInt()

    override fun forceSpeakerCompatibleMode(): Boolean {
        if (!virtualizerMode) return false
        val status = setIntParameter(PARAM_FORCE_VIRTUALIZATION_MODE, DEVICE_OUT_SPEAKER)
        if (status < 0) return false
        SessionFxStabilityPolicy.verificationRetryDelaysMs.forEach { delayMs ->
            if (delayMs > 0L) Thread.sleep(delayMs)
            if (virtualizationMode() != Virtualizer.VIRTUALIZATION_MODE_OFF) return true
        }
        return false
    }

    override fun virtualizationMode(): Int {
        if (!virtualizerMode) return Virtualizer.VIRTUALIZATION_MODE_OFF
        val device = runCatching { getIntParameter(PARAM_VIRTUALIZATION_MODE) }.getOrDefault(0)
        return when {
            device == 0 -> Virtualizer.VIRTUALIZATION_MODE_OFF
            device and DEVICE_OUT_SPEAKER != 0 -> Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
            else -> Virtualizer.VIRTUALIZATION_MODE_BINAURAL
        }
    }

    override fun close() {
        runCatching { effect.setEnabled(false) }
        runCatching { effect.release() }
    }

    private fun readStrengthSupported(): Boolean {
        val key = intBytes(PARAM_STRENGTH_SUPPORTED)
        val output = ByteArray(4)
        val status = (rawGetParameter.invoke(effect, key, output) as Number).toInt()
        check(status >= 0) { "$label strength support read failed: $status" }
        return when {
            status >= 4 -> ByteBuffer.wrap(output).order(ByteOrder.nativeOrder()).int != 0
            status >= 2 -> ByteBuffer.wrap(output).order(ByteOrder.nativeOrder()).short.toInt() != 0
            else -> false
        }
    }

    private fun setShortParameter(parameter: Int, value: Short) {
        val status = (rawSetParameter.invoke(effect, intBytes(parameter), shortBytes(value)) as Number).toInt()
        check(status >= 0) { "$label setParameter($parameter) failed: $status" }
    }

    private fun setIntParameter(parameter: Int, value: Int): Int =
        (rawSetParameter.invoke(effect, intBytes(parameter), intBytes(value)) as Number).toInt()

    private fun getShortParameter(parameter: Int): Short {
        val output = ByteArray(2)
        val status = (rawGetParameter.invoke(effect, intBytes(parameter), output) as Number).toInt()
        check(status >= 0) { "$label getParameter($parameter) failed: $status" }
        return ByteBuffer.wrap(output).order(ByteOrder.nativeOrder()).short
    }

    private fun getIntParameter(parameter: Int): Int {
        val output = ByteArray(4)
        val status = (rawGetParameter.invoke(effect, intBytes(parameter), output) as Number).toInt()
        check(status >= 0) { "$label getParameter($parameter) failed: $status" }
        return ByteBuffer.wrap(output).order(ByteOrder.nativeOrder()).int
    }

    private fun intBytes(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(value).array()
    private fun shortBytes(value: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder()).putShort(value).array()

    companion object {
        private const val PARAM_STRENGTH_SUPPORTED = 0
        private const val PARAM_STRENGTH = 1
        private const val PARAM_FORCE_VIRTUALIZATION_MODE = 3
        private const val PARAM_VIRTUALIZATION_MODE = 4
        private const val DEVICE_OUT_SPEAKER = 0x2
    }
}

private fun proxyPresent(proxyImplementation: UUID, offloadImplementation: UUID, offloadName: String): Boolean = runCatching {
    AudioEffect.queryEffects()?.any { descriptor ->
        descriptor.uuid == proxyImplementation ||
            descriptor.uuid == offloadImplementation ||
            descriptor.name.contains(offloadName, ignoreCase = true)
    } == true
}.getOrDefault(false)

class StableBassBoostEffect private constructor(private val delegate: SessionStrengthEffect) : AutoCloseable {
    val hasControl: Boolean get() = delegate.hasControl
    val strengthSupported: Boolean get() = delegate.strengthSupported
    var enabled: Boolean
        get() = delegate.enabled
        set(value) = delegate.setEnabled(value)
    val implementation: String get() = delegate.implementation
    fun setStrength(value: Int) = delegate.setStrength(value)
    fun roundedStrength(): Int = delegate.roundedStrength()
    override fun close() = delegate.close()

    companion object {
        private val PROXY = UUID.fromString("14804144-a5ee-4d24-aa88-0002a5d5c51b")
        private val OFFLOAD = UUID.fromString("2c4a8c24-1581-487f-94f6-0002a5d5c51b")
        fun proxyDetected(): Boolean = proxyPresent(PROXY, OFFLOAD, "Qti-Offload-BassBoost")
        fun open(audioSession: Int, priority: Int, allowVendorProxyStrengthEffects: Boolean = false, preferDirectSoftwareStrengthEffects: Boolean = false): StableBassBoostEffect {
            if (preferDirectSoftwareStrengthEffects) {
                return StableBassBoostEffect(
                    DirectSoftwareStrengthEffect(
                        effectType = AudioEffect.EFFECT_TYPE_BASS_BOOST,
                        implementationUuid = UUID.fromString(StandardFxCompatibilityPolicy.DIRECT_SOFTWARE_BASS_UUID),
                        priority = priority,
                        audioSession = audioSession,
                        label = "Bass Boost",
                        virtualizerMode = false,
                    ),
                )
            }
            check(allowVendorProxyStrengthEffects || !proxyDetected()) { "Bass Boost playback-session proxy is unsafe on this device" }
            return StableBassBoostEffect(PublicBassBoostEffect(priority, audioSession))
        }
    }
}

class StableVirtualizerEffect private constructor(private val delegate: SessionStrengthEffect) : AutoCloseable {
    val hasControl: Boolean get() = delegate.hasControl
    val strengthSupported: Boolean get() = delegate.strengthSupported
    var enabled: Boolean
        get() = delegate.enabled
        set(value) = delegate.setEnabled(value)
    val implementation: String get() = delegate.implementation
    fun setStrength(value: Int) = delegate.setStrength(value)
    fun roundedStrength(): Int = delegate.roundedStrength()
    fun forceSpeakerCompatibleMode(): Boolean = delegate.forceSpeakerCompatibleMode()
    fun virtualizationMode(): Int = delegate.virtualizationMode()
    override fun close() = delegate.close()

    companion object {
        private val PROXY = UUID.fromString("d3467faa-acc7-4d34-acaf-0002a5d5c51b")
        private val OFFLOAD = UUID.fromString("509a4498-561a-4bea-b3b1-0002a5d5c51b")
        fun proxyDetected(): Boolean = proxyPresent(PROXY, OFFLOAD, "Qti-Offload-Virtualizer")
        fun open(audioSession: Int, priority: Int, allowVendorProxyStrengthEffects: Boolean = false, preferDirectSoftwareStrengthEffects: Boolean = false): StableVirtualizerEffect {
            if (preferDirectSoftwareStrengthEffects) {
                return StableVirtualizerEffect(
                    DirectSoftwareStrengthEffect(
                        effectType = AudioEffect.EFFECT_TYPE_VIRTUALIZER,
                        implementationUuid = UUID.fromString(StandardFxCompatibilityPolicy.DIRECT_SOFTWARE_VIRTUALIZER_UUID),
                        priority = priority,
                        audioSession = audioSession,
                        label = "Virtualizer",
                        virtualizerMode = true,
                    ),
                )
            }
            check(allowVendorProxyStrengthEffects || !proxyDetected()) { "Virtualizer playback-session proxy is unsafe on this device" }
            return StableVirtualizerEffect(PublicVirtualizerEffect(priority, audioSession))
        }
    }
}
