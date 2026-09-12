package kr.dwas.dwas_EQ.adbbridge

object WiredAdbWireProtocol {
    const val COMMAND_PROBE = "PROBE"
    const val COMMAND_READ = "READ"
    const val COMMAND_WRITE = "WRITE"
    const val COMMAND_SAFE_PROBE = "SAFE_PROBE"
    const val COMMAND_READ_GEQ_ENABLED = "READ_GEQ_ENABLED"
    const val COMMAND_WRITE_GEQ_ENABLED = "WRITE_GEQ_ENABLED"
    const val COMMAND_INFO = "INFO"
    const val COMMAND_MEDIA_SESSIONS = "MEDIA_SESSIONS"
    const val COMMAND_SESSION_FX_APPLY = "SESSION_FX_APPLY"
    const val COMMAND_SESSION_FX_RELEASE = "SESSION_FX_RELEASE"
    const val COMMAND_CONTROL_RESET = "CONTROL_RESET"

    data class Command(val name: String, val payload: String?)

    fun commandLine(operation: String, payload: String? = null): String {
        val command = when (operation.lowercase()) {
            "probe" -> COMMAND_PROBE
            "read" -> COMMAND_READ
            "write" -> COMMAND_WRITE
            "safeprobe", "safe_probe" -> COMMAND_SAFE_PROBE
            "readgeqenabled", "read_geq_enabled" -> COMMAND_READ_GEQ_ENABLED
            "writegeqenabled", "write_geq_enabled" -> COMMAND_WRITE_GEQ_ENABLED
            "info" -> COMMAND_INFO
            "mediasessions", "media_sessions" -> COMMAND_MEDIA_SESSIONS
            "sessionfxapply", "session_fx_apply" -> COMMAND_SESSION_FX_APPLY
            "sessionfxrelease", "session_fx_release" -> COMMAND_SESSION_FX_RELEASE
            "controlreset", "control_reset" -> COMMAND_CONTROL_RESET
            else -> error("Unsupported wired ADB operation: $operation")
        }
        return if (payload.isNullOrEmpty()) command else "$command $payload"
    }

    fun parseCommand(line: String): Command {
        val trimmed = line.trim()
        require(trimmed.isNotEmpty()) { "Empty wired ADB command" }
        val separator = trimmed.indexOf(' ')
        return if (separator < 0) Command(trimmed, null)
        else Command(trimmed.substring(0, separator), trimmed.substring(separator + 1).trim().takeIf { it.isNotEmpty() })
    }
}
