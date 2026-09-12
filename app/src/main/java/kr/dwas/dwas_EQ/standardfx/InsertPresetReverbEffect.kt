package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.AudioEffect
import kr.dwas.dwas_EQ.backend.HiddenApiAccess
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class InsertPresetReverbEffect private constructor(
    private val effect: AudioEffect,
    private val rawSetParameter: Method,
    private val rawGetParameter: Method,
) : AutoCloseable {
    val hasControl: Boolean get() = runCatching { effect.hasControl() }.getOrDefault(false)

    var enabled: Boolean
        get() = runCatching { effect.enabled }.getOrDefault(false)
        set(value) {
            val status = effect.setEnabled(value)
            check(status == AudioEffect.SUCCESS) { "Insert PresetReverb setEnabled failed: $status" }
        }

    fun setPresetAndVerify(preset: Short) {
        val key = presetKey()
        val value = ByteBuffer.allocate(2).order(ByteOrder.nativeOrder()).putShort(preset).array()
        val status = (rawSetParameter.invoke(effect, key, value) as Number).toInt()
        check(status >= 0) { "Insert PresetReverb setParameter failed: $status" }
        val actual = readPreset(key)
        check(actual == preset) { "Insert PresetReverb preset readback mismatch: requested=$preset actual=$actual" }
    }

    fun verifyPresetAfterEnable(preset: Short) {
        val actual = readPreset(presetKey())
        if (actual == preset) return
        setPresetAndVerify(preset)
    }

    private fun presetKey(): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(PARAM_PRESET).array()

    private fun readPreset(key: ByteArray): Short {
        val readback = ByteArray(2)
        val getStatus = (rawGetParameter.invoke(effect, key, readback) as Number).toInt()
        check(getStatus >= 0) { "Insert PresetReverb getParameter failed: $getStatus" }
        return ByteBuffer.wrap(readback).order(ByteOrder.nativeOrder()).short
    }

    override fun close() {
        runCatching { effect.release() }
    }

    companion object {
        private const val PARAM_PRESET = 0
        private val KNOWN_INSERT_UUIDS = listOf(
            UUID.fromString("f3e178d2-ebcb-408e-8357-0002a5d5c51b"),
            UUID.fromString("172cdf00-a3bc-11df-a72f-0002a5d5c51b"),
            UUID.fromString("b08a0e38-22a5-11e2-b87b-f23c91aec05e"),
            UUID.fromString("aa2bebf6-47cf-4613-9bca-0002a5d5c51b"),
        )

        fun open(audioSession: Int, priority: Int): InsertPresetReverbEffect {
            require(audioSession > 0) { "Insert PresetReverb requires a non-zero audio session" }
            HiddenApiAccess.ensureEnabled().getOrThrow()
            val descriptors = runCatching { AudioEffect.queryEffects()?.toList().orEmpty() }.getOrDefault(emptyList())
            val insertDescriptor = descriptors.firstOrNull(::isInsertPresetReverb)
            val presetDescriptor = insertDescriptor ?: descriptors.firstOrNull(::isPresetReverb)
                ?: error("No Preset Reverb AudioEffect descriptor")
            val type = descriptorUuid(presetDescriptor, "type")
            val candidates = buildList {
                insertDescriptor?.let { descriptorUuidOrNull(it, "uuid") }?.let(::add)
                descriptors.filter(::isPresetReverb)
                    .mapNotNull { descriptorUuidOrNull(it, "uuid") }
                    .filter(KNOWN_INSERT_UUIDS::contains)
                    .forEach(::add)
                KNOWN_INSERT_UUIDS.forEach(::add)
            }.distinct()
            var lastError: Throwable? = null
            candidates.forEach { uuid ->
                val opened = runCatching { create(type, uuid, priority, audioSession) }
                if (opened.isSuccess) return opened.getOrThrow()
                lastError = opened.exceptionOrNull()
            }
            throw IllegalStateException("No insert-mode Preset Reverb implementation could be opened", lastError)
        }

        private fun create(type: UUID, uuid: UUID, priority: Int, audioSession: Int): InsertPresetReverbEffect {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ).apply { isAccessible = true }
            val effect = constructor.newInstance(type, uuid, priority, audioSession) as AudioEffect
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
                return InsertPresetReverbEffect(effect, set, get)
            } catch (t: Throwable) {
                runCatching { effect.release() }
                throw t
            }
        }

        private fun isInsertPresetReverb(descriptor: AudioEffect.Descriptor): Boolean {
            val uuid = descriptorUuidOrNull(descriptor, "uuid")
            if (uuid != null && KNOWN_INSERT_UUIDS.contains(uuid)) return true
            val name = descriptorString(descriptor, "name")
            val connectMode = descriptorString(descriptor, "connectMode")
            return name.contains("Preset", ignoreCase = true) &&
                name.contains("Reverb", ignoreCase = true) &&
                connectMode.contains("Insert", ignoreCase = true)
        }

        private fun isPresetReverb(descriptor: AudioEffect.Descriptor): Boolean {
            val name = descriptorString(descriptor, "name")
            return name.contains("Preset", ignoreCase = true) && name.contains("Reverb", ignoreCase = true)
        }

        private fun descriptorString(descriptor: AudioEffect.Descriptor, fieldName: String): String =
            runCatching { descriptor.javaClass.getField(fieldName).get(descriptor)?.toString().orEmpty() }.getOrDefault("")

        private fun descriptorUuidOrNull(descriptor: AudioEffect.Descriptor, fieldName: String): UUID? =
            runCatching { descriptor.javaClass.getField(fieldName).get(descriptor) as? UUID }.getOrNull()

        private fun descriptorUuid(descriptor: AudioEffect.Descriptor, fieldName: String): UUID =
            descriptorUuidOrNull(descriptor, fieldName) ?: error("AudioEffect descriptor $fieldName is unavailable")
    }
}
