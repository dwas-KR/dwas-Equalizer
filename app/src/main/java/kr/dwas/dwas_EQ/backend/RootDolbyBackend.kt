package kr.dwas.dwas_EQ.backend

import android.content.Context
import kr.dwas.dwas_EQ.bridge.BridgeJson
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RootDolbyBackend(context: Context, private val isOptedIn: () -> Boolean) : DolbyEqBackend {
    override val kind = BackendKind.ROOT_DOLBY
    override val supportsSafeProbe = true
    private val apkPath = context.applicationInfo.sourceDir

    private fun shellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun execute(operation: String, argument: String? = null): String {
        check(isOptedIn()) { "Root backend is disabled until the user explicitly opts in." }
        val command = buildString {
            append("CLASSPATH=").append(shellQuote(apkPath)).append(' ')
            append("app_process /system/bin kr.dwas.dwas_EQ.root.RootBridge ").append(operation)
            if (argument != null) append(' ').append(shellQuote(argument))
        }
        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
        check(process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); "Root bridge timed out" }
        val output = process.inputStream.bufferedReader().readText()
        val marker = "DWAS_EQ_RESULT="
        val line = output.lineSequence().lastOrNull { it.startsWith(marker) }
            ?: error("Root bridge returned no structured result. exit=${process.exitValue()} output=${output.take(500)}")
        return line.removePrefix(marker)
    }

    override fun probe(): BackendProbe {
        if (!isOptedIn()) return BackendProbe(kind, false, details = "Root opt-in is off.")
        return try {
            val o = JSONObject(execute("probe"))
            val gains = o.optString("gains").split(',').filter { it.isNotBlank() }
            BackendProbe(kind, o.optBoolean("ok"), o.optBoolean("hasControl"), gains.size.takeIf { it > 0 }, o.optString("message", "Root probe"))
        } catch (t: Throwable) {
            BackendProbe(kind, false, details = "${t::class.simpleName}: ${t.message ?: "Root unavailable"}")
        }
    }

    override fun readGeqEnabled(): BackendResult<Boolean> = runCatching {
        val o = JSONObject(execute("readGeqEnabled"))
        if (!o.optBoolean("ok")) BackendResult(false, message = o.optString("message", "Root GEQ enable read failed"))
        else BackendResult(true, o.getBoolean("enabled"), o.optString("message", "Root GEQ enable read"))
    }.getOrElse { BackendResult(false, message = "Root GEQ enable read failed: ${it.message}") }

    override fun writeGeqEnabled(enabled: Boolean): BackendResult<Unit> = runCatching {
        BridgeJson.parseUnit(execute("writeGeqEnabled", enabled.toString()))
    }.getOrElse { BackendResult(false, message = "Root GEQ enable write failed: ${it.message}") }

    override fun readGains(): BackendResult<IntArray> = runCatching { BridgeJson.parseGains(execute("read")) }
        .getOrElse { BackendResult(false, message = "Root read failed: ${it.message}") }

    override fun writeGains(gains: IntArray): BackendResult<Unit> = runCatching { BridgeJson.parseUnit(execute("write", gains.joinToString(","))) }
        .getOrElse { BackendResult(false, message = "Root write failed: ${it.message}") }

    fun safeProbe(): BackendResult<Unit> = runCatching { BridgeJson.parseUnit(execute("safeProbe")) }
        .getOrElse { BackendResult(false, message = "Root safe probe failed: ${it.message}") }
}
