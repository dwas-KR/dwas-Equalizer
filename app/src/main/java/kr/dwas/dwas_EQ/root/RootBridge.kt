package kr.dwas.dwas_EQ.root

import kr.dwas.dwas_EQ.backend.RawAudioEffectSession
import kr.dwas.dwas_EQ.core.SafeProbeEngine
import org.lsposed.hiddenapibypass.HiddenApiBypass
import kr.dwas.dwas_EQ.bridge.BridgeJson

object RootBridge {
    @JvmStatic fun main(args: Array<String>) {
        val response = try {
            HiddenApiBypass.setHiddenApiExemptions("")
            when (args.firstOrNull()) {
                "probe" -> RawAudioEffectSession.open().use { s ->
                    val gains = if (s.hasControl) runCatching { s.readGains() }.getOrNull() else null
                    BridgeJson.ok("Root DAP probe", gains, mapOf("hasControl" to s.hasControl))
                }
                "readGeqEnabled" -> RawAudioEffectSession.open().use { s ->
                    if (!s.hasControl) BridgeJson.error("Root DAP hasControl() is false") else BridgeJson.ok("Root Dolby GEQ enabled state read", extras = mapOf("enabled" to s.readGeqEnabled()))
                }
                "writeGeqEnabled" -> RawAudioEffectSession.open().use { s ->
                    if (!s.hasControl) BridgeJson.error("Root DAP hasControl() is false")
                    else {
                        val enabled = args.getOrNull(1).orEmpty().toBooleanStrict()
                        s.writeGeqEnabled(enabled)
                        if (s.readGeqEnabled() == enabled) BridgeJson.ok("Root Dolby GEQ enabled state verified") else BridgeJson.error("Root Dolby GEQ enabled state readback differs")
                    }
                }
                "read" -> RawAudioEffectSession.open().use { s ->
                    if (!s.hasControl) BridgeJson.error("Root DAP hasControl() is false") else BridgeJson.ok("Root Dolby gains read", s.readGains())
                }
                "write" -> {
                    val gains = BridgeJson.gainsFromCsv(args.getOrNull(1).orEmpty())
                    RawAudioEffectSession.open().use { s ->
                        if (!s.hasControl) BridgeJson.error("Root DAP hasControl() is false")
                        else { s.writeGains(gains); if (s.readGains().contentEquals(gains)) BridgeJson.ok("Root Dolby write/readback verified") else BridgeJson.error("Root readback differs") }
                    }
                }
                "safeProbe" -> RawAudioEffectSession.open().use { s ->
                    if (!s.hasControl) BridgeJson.error("Root DAP hasControl() is false")
                    else SafeProbeEngine.run(s).let { r -> if (r.writeVerified && r.restoreVerified) BridgeJson.ok("Root safe probe verified and restored", extras = mapOf("delta" to r.deltaApplied)) else BridgeJson.error("Root safe probe write=${r.writeVerified}, restore=${r.restoreVerified}") }
                }
                else -> BridgeJson.error("Unknown RootBridge operation")
            }
        } catch (t: Throwable) {
            BridgeJson.error("${t::class.simpleName}: ${t.message ?: "Root bridge failed"}")
        }
        println("DWAS_EQ_RESULT=$response")
    }
}
