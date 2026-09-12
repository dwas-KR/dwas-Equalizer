package kr.dwas.dwas_EQ.domain

import java.nio.charset.StandardCharsets
import java.util.Base64

data class EqPreset(val name: String, val curve: EqCurve) {
    init { require(name.trim().isNotEmpty()) { "Preset name must not be blank" } }
}


object PresetCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(presets: List<EqPreset>): String = presets.joinToString(";") { preset ->
        val name = encoder.encodeToString(preset.name.toByteArray(StandardCharsets.UTF_8))
        val gains = preset.curve.gainsDb.joinToString(",") { it.toString() }
        "$name:$gains"
    }

    fun decode(raw: String?): List<EqPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';').mapNotNull { entry ->
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
            runCatching {
                val name = String(decoder.decode(entry.substring(0, separator)), StandardCharsets.UTF_8)
                val values = entry.substring(separator + 1).split(',').map { it.toFloat() }
                EqPreset(name, EqCurve.fromStoredValues(values))
            }.getOrNull()
        }
    }
}
