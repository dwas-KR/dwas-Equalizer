package kr.dwas.dwas_EQ.backend

import android.content.Context
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeRegistry
import kr.dwas.dwas_EQ.adbbridge.WiredAdbSocketClient
import kr.dwas.dwas_EQ.bridge.BridgeJson
import org.json.JSONObject

class WiredAdbDolbyBackend(context: Context) : DolbyEqBackend {
    override val kind = BackendKind.WIRED_ADB_DOLBY
    override val supportsSafeProbe = true
    private val client = WiredAdbSocketClient(context)

    override fun probe(): BackendProbe = try {
        val info = JSONObject(client.request("probe"))
        val gains = info.optString("gains").split(',').filter { it.isNotBlank() }
        BackendProbe(
            backend = kind,
            available = info.optBoolean("ok"),
            hasControl = info.optBoolean("hasControl"),
            gainCount = gains.size.takeIf { it > 0 },
            details = info.optString("message", "Wired ADB DAP probe"),
        )
    } catch (t: Throwable) {
        BackendProbe(kind, false, details = WiredAdbBridgeRegistry.statusText())
    }

    override fun readGeqEnabled(): BackendResult<Boolean> = runCatching {
        val info = JSONObject(client.request("read_geq_enabled"))
        if (!info.optBoolean("ok")) BackendResult(false, message = info.optString("message", "Wired ADB GEQ enable read failed"))
        else BackendResult(true, info.getBoolean("enabled"), info.optString("message", "Wired ADB GEQ enable read"))
    }.getOrElse { BackendResult(false, message = "Wired ADB GEQ enable read failed: ${it.message}") }

    override fun writeGeqEnabled(enabled: Boolean): BackendResult<Unit> = runCatching {
        BridgeJson.parseUnit(client.request("write_geq_enabled", enabled.toString()))
    }.getOrElse { BackendResult(false, message = "Wired ADB GEQ enable write failed: ${it.message}") }

    override fun readGains(): BackendResult<IntArray> = runCatching {
        BridgeJson.parseGains(client.request("read"))
    }.getOrElse { BackendResult(false, message = "Wired ADB read failed: ${it.message}") }

    override fun writeGains(gains: IntArray): BackendResult<Unit> = runCatching {
        BridgeJson.parseUnit(client.request("write", gains.joinToString(",")))
    }.getOrElse { BackendResult(false, message = "Wired ADB write failed: ${it.message}") }

    fun safeProbe(): BackendResult<Unit> = runCatching {
        BridgeJson.parseUnit(client.request("safeProbe"))
    }.getOrElse { BackendResult(false, message = "Wired ADB safe probe failed: ${it.message}") }
}
