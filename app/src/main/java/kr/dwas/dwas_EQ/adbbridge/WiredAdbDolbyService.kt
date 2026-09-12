package kr.dwas.dwas_EQ.adbbridge

import android.os.Process
import android.os.SystemClock
import kr.dwas.dwas_EQ.backend.RawAudioEffectSession
import kr.dwas.dwas_EQ.bridge.BridgeJson
import kr.dwas.dwas_EQ.core.SafeProbeEngine
import kr.dwas.dwas_EQ.standardfx.AudioSessionDiscoveryParser
import kr.dwas.dwas_EQ.standardfx.SessionStandardFxController
import kr.dwas.dwas_EQ.standardfx.StandardFxCodec
import kr.dwas.dwas_EQ.standardfx.StandardFxSettings
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class WiredAdbDolbyService {
    private val sessionFxControllers = linkedMapOf<Int, SessionStandardFxController>()
    private var lastSessionFxDiscoveryAtMs = 0L
    fun probe(): String = guarded {
        RawAudioEffectSession.open().use { session ->
            val gains = if (session.hasControl) runCatching { session.readGains() }.getOrNull() else null
            BridgeJson.ok(
                message = if (session.hasControl) "Wired ADB DAP is controllable." else "Wired ADB DAP opened but hasControl() is false.",
                gains = gains,
                extras = mapOf(
                    "hasControl" to session.hasControl,
                    "uid" to Process.myUid(),
                    "pid" to Process.myPid(),
                ),
            )
        }
    }

    fun readGains(): String = guarded {
        RawAudioEffectSession.open().use { session ->
            if (!session.hasControl) BridgeJson.error("Wired ADB DAP hasControl() is false")
            else BridgeJson.ok("Wired ADB Dolby gains read", session.readGains())
        }
    }

    fun writeGains(csv: String): String = guarded {
        val gains = BridgeJson.gainsFromCsv(csv)
        RawAudioEffectSession.open().use { session ->
            if (!session.hasControl) {
                BridgeJson.error("Wired ADB DAP hasControl() is false")
            } else {
                session.writeGains(gains)
                if (session.readGains().contentEquals(gains)) {
                    BridgeJson.ok("Wired ADB Dolby write/readback verified")
                } else {
                    BridgeJson.error("Wired ADB Dolby readback differs")
                }
            }
        }
    }

    fun readGeqEnabled(): String = guarded {
        RawAudioEffectSession.open().use { session ->
            if (!session.hasControl) BridgeJson.error("Wired ADB DAP hasControl() is false")
            else BridgeJson.ok("Wired ADB Dolby GEQ enabled state read", extras = mapOf("enabled" to session.readGeqEnabled()))
        }
    }

    fun writeGeqEnabled(raw: String): String = guarded {
        val enabled = raw.toBooleanStrict()
        RawAudioEffectSession.open().use { session ->
            if (!session.hasControl) BridgeJson.error("Wired ADB DAP hasControl() is false")
            else {
                session.writeGeqEnabled(enabled)
                if (session.readGeqEnabled() == enabled) BridgeJson.ok("Wired ADB Dolby GEQ enabled state verified")
                else BridgeJson.error("Wired ADB Dolby GEQ enabled state readback differs")
            }
        }
    }

    fun safeProbe(): String = guarded {
        RawAudioEffectSession.open().use { session ->
            if (!session.hasControl) {
                BridgeJson.error("Wired ADB DAP hasControl() is false")
            } else {
                val result = SafeProbeEngine.run(session)
                if (result.writeVerified && result.restoreVerified) {
                    BridgeJson.ok(
                        "Wired ADB safe probe verified and original gains restored",
                        extras = mapOf("delta" to result.deltaApplied),
                    )
                } else {
                    BridgeJson.error(
                        "Wired ADB safe probe write=${result.writeVerified}, restore=${result.restoreVerified}, error=${result.error ?: "none"}"
                    )
                }
            }
        }
    }


    



    fun mediaSessions(): String = guarded {
        val sessions = discoverMediaSessions()
        BridgeJson.ok(
            message = if (sessions.isEmpty()) "No active USAGE_MEDIA session" else "Active USAGE_MEDIA sessions discovered",
            extras = mapOf("sessions" to sessions.sorted().joinToString(",")),
        )
    }

    @Synchronized
    fun sessionFxApply(encodedSettings: String): String = guarded {
        val fullDynamicsRouting = encodedSettings.startsWith("FULL|")
        val speakerSafeRouting = encodedSettings.startsWith("SPEAKER_SAFE|")
        val forceRouting = encodedSettings.startsWith("FORCE|")
        val directSoftwareRouting = encodedSettings.startsWith("SOFTWARE|")
        val directSoftwareForceRouting = encodedSettings.startsWith("SOFTWARE_FORCE|")
        val proxyRouting = encodedSettings.startsWith("PROXY|")
        val proxyForceRouting = encodedSettings.startsWith("PROXY_FORCE|")
        val forceVirtualizerMode = speakerSafeRouting || forceRouting || proxyForceRouting || directSoftwareForceRouting
        val environmentalReverbRouting = speakerSafeRouting
        val allowVendorProxyStrengthEffects = proxyRouting || proxyForceRouting
        val preferDirectSoftwareStrengthEffects = directSoftwareRouting || directSoftwareForceRouting
        val rawSettings = when {
            fullDynamicsRouting -> encodedSettings.removePrefix("FULL|")
            speakerSafeRouting -> encodedSettings.removePrefix("SPEAKER_SAFE|")
            forceRouting -> encodedSettings.removePrefix("FORCE|")
            directSoftwareRouting -> encodedSettings.removePrefix("SOFTWARE|")
            directSoftwareForceRouting -> encodedSettings.removePrefix("SOFTWARE_FORCE|")
            proxyRouting -> encodedSettings.removePrefix("PROXY|")
            proxyForceRouting -> encodedSettings.removePrefix("PROXY_FORCE|")
            else -> encodedSettings
        }
        val settings = StandardFxCodec.decode(rawSettings).normalized()
        applyToExistingSessionFxControllers(settings, fullDynamicsRouting, forceVirtualizerMode, environmentalReverbRouting, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)?.let { return@guarded it }
        val sessions = discoverMediaSessions().filter { it > 0 }.toSet()
        lastSessionFxDiscoveryAtMs = SystemClock.elapsedRealtime()
        releaseStaleSessionFx(sessions)
        if (sessions.isEmpty()) {
            releaseSessionFxControllers()
            return@guarded BridgeJson.error("No active USAGE_MEDIA session for standard session effects")
        }

        val active = linkedSetOf<String>()
        val failures = linkedMapOf<String, String>()
        sessions.sorted().forEach { sessionId ->
            val controller = sessionFxControllers.getOrPut(sessionId) { SessionStandardFxController(sessionId) }
            val result = controller.apply(settings, fullDynamicsRouting, forceVirtualizerMode, environmentalReverbRouting, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)
            active += result.activeEffects
            result.failedEffects.forEach { (name, reason) -> failures["$name (session $sessionId)"] = reason }
        }

        if (failures.isNotEmpty()) {
            val detail = failures.entries.joinToString(" | ") { "${it.key}: ${it.value}" }
            BridgeJson.ok(
                message = "Shell session FX partial: $detail",
                extras = mapOf(
                    "sessions" to sessions.sorted().joinToString(","),
                    "active" to active.sorted().joinToString("|"),
                    "failures" to JSONObject(failures).toString(),
                    "partial" to true,
                ),
            )
        } else {
            BridgeJson.ok(
                message = "Shell session FX active on ${sessions.sorted().joinToString(",")}",
                extras = mapOf(
                    "sessions" to sessions.sorted().joinToString(","),
                    "active" to active.sorted().joinToString("|"),
                ),
            )
        }
    }


    private fun applyToExistingSessionFxControllers(settings: StandardFxSettings, fullDynamicsRouting: Boolean, forceVirtualizerMode: Boolean, environmentalReverbRouting: Boolean, allowVendorProxyStrengthEffects: Boolean, preferDirectSoftwareStrengthEffects: Boolean): String? {
        if (sessionFxControllers.isEmpty()) return null
        if (SystemClock.elapsedRealtime() - lastSessionFxDiscoveryAtMs > SESSION_FX_FAST_PATH_MS) return null
        val active = linkedSetOf<String>()
        val failures = linkedMapOf<String, String>()
        sessionFxControllers.toSortedMap().forEach { (sessionId, controller) ->
            val result = controller.apply(settings, fullDynamicsRouting, forceVirtualizerMode, environmentalReverbRouting, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)
            active += result.activeEffects
            result.failedEffects.forEach { (name, reason) -> failures["$name (session $sessionId)"] = reason }
        }
        if (failures.isNotEmpty()) {
            val detail = failures.entries.joinToString(" | ") { "${it.key}: ${it.value}" }
            return BridgeJson.ok(
                message = "Shell session FX partial realtime update: $detail",
                extras = mapOf(
                    "sessions" to sessionFxControllers.keys.sorted().joinToString(","),
                    "active" to active.sorted().joinToString("|"),
                    "failures" to JSONObject(failures).toString(),
                    "partial" to true,
                ),
            )
        }
        return BridgeJson.ok(
            message = "Shell session FX realtime update on ${sessionFxControllers.keys.sorted().joinToString(",")}",
            extras = mapOf(
                "sessions" to sessionFxControllers.keys.sorted().joinToString(","),
                "active" to active.sorted().joinToString("|"),
            ),
        )
    }

    @Synchronized
    fun sessionFxRelease(): String = guarded {
        releaseSessionFxControllers()
        BridgeJson.ok("Shell session FX released")
    }

    @Synchronized
    fun controlReset(): String = guarded {
        releaseSessionFxControllers()
        val logRemoved = runCatching {
            val file = File(BRIDGE_LOG_PATH)
            !file.exists() || file.delete()
        }.getOrDefault(false)
        val dumpRevoked = revokePermission("android.permission.DUMP")
        val audioSettingsRevoked = revokePermission("android.permission.MODIFY_AUDIO_SETTINGS")
        thread(name = "dwas-eq-control-reset-exit", isDaemon = true) {
            Thread.sleep(CONTROL_RESET_EXIT_DELAY_MS)
            kotlin.system.exitProcess(0)
        }
        BridgeJson.ok(
            "Wired ADB control access reset",
            extras = mapOf(
                "logRemoved" to logRemoved,
                "dumpRevoked" to dumpRevoked,
                "audioSettingsRevoked" to audioSettingsRevoked,
            ),
        )
    }

    private fun revokePermission(permission: String): Boolean = runCatching {
        val process = ProcessBuilder("/system/bin/pm", "revoke", PACKAGE_NAME, permission)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(PERMISSION_REVOKE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    }.getOrDefault(false)

    private fun discoverMediaSessions(): Set<Int> {
        val process = ProcessBuilder("/system/bin/dumpsys", "audio")
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = thread(name = "dwas-eq-audio-dump", isDaemon = true) {
            process.inputStream.bufferedReader().use { output.append(it.readText()) }
        }
        if (!process.waitFor(MEDIA_SESSION_DUMP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(500L)
            error("dumpsys audio timed out")
        }
        reader.join(1_000L)
        check(process.exitValue() == 0) { "dumpsys audio exit=${process.exitValue()}" }
        return AudioSessionDiscoveryParser.parse(output.toString())
    }

    private fun releaseStaleSessionFx(activeSessions: Set<Int>) {
        val stale = sessionFxControllers.keys.filterNot(activeSessions::contains)
        stale.forEach { id -> sessionFxControllers.remove(id)?.close() }
    }

    private fun releaseSessionFxControllers() {
        sessionFxControllers.values.forEach { it.close() }
        sessionFxControllers.clear()
        lastSessionFxDiscoveryAtMs = 0L
    }

    fun bridgeInfo(): String = BridgeJson.ok(
        "dwas_EQ wired ADB bridge",
        extras = mapOf("uid" to Process.myUid(), "pid" to Process.myPid()),
    )

    private inline fun guarded(block: () -> String): String = try {
        block()
    } catch (t: Throwable) {
        BridgeJson.error("${t::class.simpleName}: ${t.message ?: "Wired ADB bridge operation failed"}")
    }

    companion object {
        private const val MEDIA_SESSION_DUMP_TIMEOUT_SECONDS = 4L
        private const val SESSION_FX_FAST_PATH_MS = 750L
        private const val PERMISSION_REVOKE_TIMEOUT_SECONDS = 2L
        private const val CONTROL_RESET_EXIT_DELAY_MS = 300L
        private const val PACKAGE_NAME = "kr.dwas.dwas_EQ"
        private const val BRIDGE_LOG_PATH = "/data/local/tmp/dwas_eq_adb.log"
    }
}
