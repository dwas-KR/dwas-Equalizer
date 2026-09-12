package kr.dwas.dwas_EQ.standardfx

import android.content.Context
import kr.dwas.dwas_EQ.adbbridge.WiredAdbSocketClient
import org.json.JSONObject

data class SessionFxBridgeResult(
    val ok: Boolean,
    val activeEffects: Set<String>,
    val failedEffects: Map<String, String> = emptyMap(),
    val detail: String,
)

class WiredAdbSessionFxClient(context: Context) {
    private val client = WiredAdbSocketClient(context.applicationContext)

    fun apply(settings: StandardFxSettings, fullDynamicsRouting: Boolean = false, forceVirtualizerMode: Boolean = false, environmentalReverbRouting: Boolean = false, allowVendorProxyStrengthEffects: Boolean = false, preferDirectSoftwareStrengthEffects: Boolean = false): SessionFxBridgeResult? = runCatching {
        val encoded = StandardFxCodec.encode(settings)
        val payload = when {
            preferDirectSoftwareStrengthEffects && forceVirtualizerMode -> "SOFTWARE_FORCE|$encoded"
            preferDirectSoftwareStrengthEffects -> "SOFTWARE|$encoded"
            allowVendorProxyStrengthEffects && forceVirtualizerMode -> "PROXY_FORCE|$encoded"
            allowVendorProxyStrengthEffects -> "PROXY|$encoded"
            forceVirtualizerMode && environmentalReverbRouting -> "SPEAKER_SAFE|$encoded"
            forceVirtualizerMode -> "FORCE|$encoded"
            fullDynamicsRouting -> "FULL|$encoded"
            else -> encoded
        }
        val response = client.request("session_fx_apply", payload)
        val json = JSONObject(response)
        val ok = json.optBoolean("ok", false)
        val active = json.optString("active", "")
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        SessionFxBridgeResult(
            ok = ok,
            activeEffects = active,
            failedEffects = parseFailures(json.optString("failures", "")),
            detail = json.optString("message", if (ok) "Shell session FX active" else "Shell session FX failed"),
        )
    }.getOrNull()

    private fun parseFailures(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, json.optString(key, "Session effect failed"))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun release() {
        runCatching { client.request("session_fx_release") }
    }
}
