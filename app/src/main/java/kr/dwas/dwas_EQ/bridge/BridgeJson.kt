package kr.dwas.dwas_EQ.bridge

import kr.dwas.dwas_EQ.backend.BackendResult
import org.json.JSONObject

object BridgeJson {
    fun ok(message: String, gains: IntArray? = null, extras: Map<String, Any?> = emptyMap()): String = JSONObject().apply {
        put("ok", true)
        put("message", message)
        if (gains != null) put("gains", gains.joinToString(","))
        extras.forEach { (k, v) -> put(k, v) }
    }.toString()

    fun error(message: String): String = JSONObject().put("ok", false).put("message", message).toString()

    fun gainsFromCsv(csv: String): IntArray = csv.split(',').filter { it.isNotBlank() }.map { it.trim().toInt() }.toIntArray().also {
        require(it.size == 20) { "Expected 20 gains" }
    }

    fun parseGains(json: String): BackendResult<IntArray> = runCatching {
        val o = JSONObject(json)
        if (!o.optBoolean("ok")) return BackendResult(false, message = o.optString("message", "Bridge failed"))
        BackendResult(true, gainsFromCsv(o.getString("gains")), o.optString("message", "OK"))
    }.getOrElse { BackendResult(false, message = "Bridge response error: ${it.message}") }

    fun parseUnit(json: String): BackendResult<Unit> = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("ok")) BackendResult(true, Unit, o.optString("message", "OK"))
        else BackendResult(false, message = o.optString("message", "Bridge failed"))
    }.getOrElse { BackendResult(false, message = "Bridge response error: ${it.message}") }
}
