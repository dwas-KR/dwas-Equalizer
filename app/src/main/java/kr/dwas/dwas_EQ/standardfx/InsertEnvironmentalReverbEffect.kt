package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.AudioEffect
import kr.dwas.dwas_EQ.backend.HiddenApiAccess
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class InsertEnvironmentalReverbEffect private constructor(
    private val effect: AudioEffect,
    private val rawSetParameter: Method,
    private val rawGetParameter: Method,
) : AutoCloseable {
    val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)

    var enabled: Boolean
        get() = runCatching { effect.enabled }.getOrDefault(false)
        set(value) {
            val status = effect.setEnabled(value)
            check(status == AudioEffect.SUCCESS) { "Insert EnvironmentalReverb setEnabled failed: $status" }
        }

    fun setSettingsAndVerify(settings: DwasEnvironmentalReverbSettings) {
        writeShort(PARAM_ROOM_LEVEL, settings.roomLevelMb)
        writeShort(PARAM_ROOM_HF_LEVEL, settings.roomHfLevelMb)
        writeInt(PARAM_DECAY_TIME, settings.decayTimeMs)
        writeShort(PARAM_DECAY_HF_RATIO, settings.decayHfRatioPermille)
        writeShort(PARAM_REFLECTIONS_LEVEL, settings.reflectionsLevelMb)
        writeInt(PARAM_REFLECTIONS_DELAY, settings.reflectionsDelayMs)
        writeShort(PARAM_REVERB_LEVEL, settings.reverbLevelMb)
        writeInt(PARAM_REVERB_DELAY, settings.reverbDelayMs)
        writeShort(PARAM_DIFFUSION, settings.diffusionPermille)
        writeShort(PARAM_DENSITY, settings.densityPermille)
        verifySettings(settings)
    }

    fun verifySettingsAfterEnable(settings: DwasEnvironmentalReverbSettings) {
        verifySettings(settings)
    }

    private fun verifySettings(settings: DwasEnvironmentalReverbSettings) {
        verifyReadback(PARAM_ROOM_LEVEL, settings.roomLevelMb.toInt(), readShort(PARAM_ROOM_LEVEL).toInt(), "room level")
        verifyReadback(PARAM_ROOM_HF_LEVEL, settings.roomHfLevelMb.toInt(), readShort(PARAM_ROOM_HF_LEVEL).toInt(), "room HF level")
        verifyReadback(PARAM_DECAY_TIME, settings.decayTimeMs, readInt(PARAM_DECAY_TIME), "decay time")
        verifyReadback(PARAM_DECAY_HF_RATIO, settings.decayHfRatioPermille.toInt(), readShort(PARAM_DECAY_HF_RATIO).toInt(), "decay HF ratio")
        verifyReadback(PARAM_REFLECTIONS_LEVEL, settings.reflectionsLevelMb.toInt(), readShort(PARAM_REFLECTIONS_LEVEL).toInt(), "reflections level")
        verifyReadback(PARAM_REFLECTIONS_DELAY, settings.reflectionsDelayMs, readInt(PARAM_REFLECTIONS_DELAY), "reflections delay")
        verifyReadback(PARAM_REVERB_LEVEL, settings.reverbLevelMb.toInt(), readShort(PARAM_REVERB_LEVEL).toInt(), "reverb level")
        verifyReadback(PARAM_REVERB_DELAY, settings.reverbDelayMs, readInt(PARAM_REVERB_DELAY), "reverb delay")
        verifyReadback(PARAM_DIFFUSION, settings.diffusionPermille.toInt(), readShort(PARAM_DIFFUSION).toInt(), "diffusion")
        verifyReadback(PARAM_DENSITY, settings.densityPermille.toInt(), readShort(PARAM_DENSITY).toInt(), "density")
    }

    private fun verifyReadback(parameter: Int, expected: Int, actual: Int, name: String) {
        check(SessionFxStabilityPolicy.environmentalReverbReadbackMatches(parameter, expected, actual)) {
            "EnvironmentalReverb $name readback mismatch: expected=$expected actual=$actual"
        }
    }

    private fun writeShort(parameter: Int, value: Short) {
        val status = (rawSetParameter.invoke(effect, key(parameter), shortValue(value)) as Number).toInt()
        check(status >= 0) { "EnvironmentalReverb setParameter $parameter failed: $status" }
    }

    private fun writeInt(parameter: Int, value: Int) {
        val status = (rawSetParameter.invoke(effect, key(parameter), intValue(value)) as Number).toInt()
        check(status >= 0) { "EnvironmentalReverb setParameter $parameter failed: $status" }
    }

    private fun readShort(parameter: Int): Short {
        val value = ByteArray(2)
        val status = (rawGetParameter.invoke(effect, key(parameter), value) as Number).toInt()
        check(status >= 0) { "EnvironmentalReverb getParameter $parameter failed: $status" }
        return ByteBuffer.wrap(value).order(ByteOrder.nativeOrder()).short
    }

    private fun readInt(parameter: Int): Int {
        val value = ByteArray(4)
        val status = (rawGetParameter.invoke(effect, key(parameter), value) as Number).toInt()
        check(status >= 0) { "EnvironmentalReverb getParameter $parameter failed: $status" }
        return ByteBuffer.wrap(value).order(ByteOrder.nativeOrder()).int
    }

    private fun key(parameter: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(parameter).array()
    private fun shortValue(value: Short): ByteArray = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder()).putShort(value).array()
    private fun intValue(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(value).array()

    override fun close() {
        runCatching { effect.setEnabled(false) }
        runCatching { effect.release() }
    }

    companion object {
        const val PARAM_ROOM_LEVEL = 0
        const val PARAM_ROOM_HF_LEVEL = 1
        const val PARAM_DECAY_TIME = 2
        const val PARAM_DECAY_HF_RATIO = 3
        const val PARAM_REFLECTIONS_LEVEL = 4
        const val PARAM_REFLECTIONS_DELAY = 5
        const val PARAM_REVERB_LEVEL = 6
        const val PARAM_REVERB_DELAY = 7
        const val PARAM_DIFFUSION = 8
        const val PARAM_DENSITY = 9
        private val TYPE_UUID = UUID.fromString("c2e5d5f0-94bd-4763-9cac-4e234d06839e")
        private val INSERT_UUID = UUID.fromString("c7a511a0-a3bb-11df-860e-0002a5d5c51b")

        fun open(audioSession: Int, priority: Int): InsertEnvironmentalReverbEffect {
            require(audioSession > 0) { "Insert EnvironmentalReverb requires a non-zero audio session" }
            HiddenApiAccess.ensureEnabled().getOrThrow()
            val descriptors = runCatching { AudioEffect.queryEffects()?.toList().orEmpty() }.getOrDefault(emptyList())
            check(descriptors.any { descriptorUuidOrNull(it, "uuid") == INSERT_UUID }) { "Insert EnvironmentalReverb descriptor unavailable" }
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val effect = constructor.newInstance(TYPE_UUID, INSERT_UUID, priority, audioSession) as AudioEffect
            try {
                val set = AudioEffect::class.java.getDeclaredMethod(
                    "setParameter",
                    ByteArray::class.java,
                    ByteArray::class.java,
                ).apply { isAccessible = true }
                val get = AudioEffect::class.java.getDeclaredMethod(
                    "getParameter",
                    ByteArray::class.java,
                    ByteArray::class.java,
                ).apply { isAccessible = true }
                return InsertEnvironmentalReverbEffect(effect, set, get)
            } catch (t: Throwable) {
                runCatching { effect.release() }
                throw t
            }
        }

        private fun descriptorUuidOrNull(descriptor: AudioEffect.Descriptor, fieldName: String): UUID? =
            runCatching { descriptor.javaClass.getField(fieldName).get(descriptor) as? UUID }.getOrNull()
    }
}
