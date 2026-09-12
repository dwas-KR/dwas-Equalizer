package kr.dwas.dwas_EQ.standardfx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeAuth
import kr.dwas.dwas_EQ.adbbridge.WiredAdbSocketClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread







data class AudioSessionDiscoveryResult(
    val sessions: Set<Int>,
    val dumpPermissionGranted: Boolean,
    val detail: String,
    val shellBridgeAvailable: Boolean = false,
) {
    val sessionAccessAvailable: Boolean get() = shellBridgeAvailable || dumpPermissionGranted || sessions.isNotEmpty()
}

object AudioEffectSessionRegistry {
    private val sessions = linkedSetOf<Int>()

    @Synchronized fun opened(sessionId: Int) {
        if (sessionId > 0) {
            sessions += sessionId
            AudioSessionDiscovery.invalidateCache()
        }
    }

    @Synchronized fun closed(sessionId: Int) {
        sessions -= sessionId
        AudioSessionDiscovery.invalidateCache()
    }

    @Synchronized fun snapshot(): Set<Int> = sessions.toSet()
}

class AudioSessionDiscovery(private val context: Context) {
    @Synchronized
    fun discover(force: Boolean = false): AudioSessionDiscoveryResult {
        val fromBroadcast = AudioEffectSessionRegistry.snapshot().toMutableSet()
        val dumpGranted = context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
        val bridgeArmed = WiredAdbBridgeAuth.loadEndpoint(context.applicationContext) != null
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = cachedResult
        if (!force && cached != null && cached.dumpPermissionGranted == dumpGranted && now - cachedAtMs < CACHE_MS) {
            return cached.copy(sessions = cached.sessions + fromBroadcast)
        }

        if (bridgeArmed) {
            readFromShellBridge()?.let { bridge ->
                val sessions = (bridge.sessions + fromBroadcast).filter { it > 0 }.toSet()
                return AudioSessionDiscoveryResult(
                    sessions = sessions,
                    dumpPermissionGranted = dumpGranted,
                    shellBridgeAvailable = true,
                    detail = when {
                        sessions.isNotEmpty() -> "Active media sessions: ${sessions.sorted().joinToString()} (wired ADB shell bridge)"
                        else -> "Wired ADB shell bridge is connected; no started USAGE_MEDIA session is currently visible"
                    },
                ).also { updateCache(it, now) }
            }
        }

        if (!dumpGranted) {
            return AudioSessionDiscoveryResult(
                sessions = fromBroadcast,
                dumpPermissionGranted = false,
                shellBridgeAvailable = false,
                detail = if (fromBroadcast.isEmpty()) {
                    "Audio session access is waiting for the wired ADB shell bridge, optional ADB DUMP grant, or a player OPEN_AUDIO_EFFECT_CONTROL_SESSION broadcast"
                } else {
                    "Audio sessions from player broadcast: ${fromBroadcast.sorted().joinToString()}"
                },
            ).also { updateCache(it, now) }
        }

        val dumpResult = runCatching { readAudioDump() }
        val parsed = dumpResult.getOrNull()?.let(AudioSessionDiscoveryParser::parse).orEmpty()
        fromBroadcast += parsed
        val detail = when {
            fromBroadcast.isNotEmpty() -> "Active media sessions: ${fromBroadcast.sorted().joinToString()} (ADB DUMP fallback)"
            dumpResult.isFailure -> "ADB DUMP is granted but dumpsys audio failed: ${dumpResult.exceptionOrNull()?.message ?: "unknown error"}"
            else -> "ADB DUMP is granted; no started USAGE_MEDIA session is currently visible"
        }
        return AudioSessionDiscoveryResult(fromBroadcast, true, detail, shellBridgeAvailable = false).also { updateCache(it, now) }
    }

    fun statusText(): String {
        val dumpGranted = context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
        val bridgeArmed = WiredAdbBridgeAuth.loadEndpoint(context.applicationContext) != null
        val broadcastCount = AudioEffectSessionRegistry.snapshot().size
        return when {
            bridgeArmed -> "Wired ADB shell session discovery armed"
            dumpGranted -> "ADB DUMP session discovery enabled"
            broadcastCount > 0 -> "Player session broadcast available ($broadcastCount session(s))"
            else -> "Session discovery unavailable; rerun tools\\dwas_EQ_ADB_Enable.bat after installing this build"
        }
    }

    private fun readFromShellBridge(): BridgeSessionResult? = runCatching {
        val response = WiredAdbSocketClient(context.applicationContext).request("media_sessions")
        val json = JSONObject(response)
        check(json.optBoolean("ok", false)) { json.optString("message", "Wired ADB media-session query failed") }
        val sessions = json.optString("sessions", "")
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it > 0 }
            .toSet()
        BridgeSessionResult(sessions)
    }.getOrNull()

    private fun readAudioDump(): String {
        val process = ProcessBuilder("/system/bin/dumpsys", "audio")
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = thread(name = "dwas-eq-local-audio-dump", isDaemon = true) {
            process.inputStream.bufferedReader().use { output.append(it.readText()) }
        }
        if (!process.waitFor(DUMP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join(500L)
            error("dumpsys audio timed out")
        }
        reader.join(1_000L)
        check(process.exitValue() == 0) { "dumpsys audio exit=${process.exitValue()}: ${output.take(240)}" }
        return output.toString()
    }

    private data class BridgeSessionResult(val sessions: Set<Int>)

    companion object {
        private const val DUMP_TIMEOUT_SECONDS = 4L
        private const val CACHE_MS = 750L
        @Volatile private var cachedResult: AudioSessionDiscoveryResult? = null
        @Volatile private var cachedAtMs: Long = 0L

        @Synchronized
        fun invalidateCache() {
            cachedResult = null
            cachedAtMs = 0L
        }

        private fun updateCache(result: AudioSessionDiscoveryResult, now: Long) {
            cachedResult = result
            cachedAtMs = now
        }
    }
}
