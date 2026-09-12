package kr.dwas.dwas_EQ.adbbridge

import android.content.Context

object WiredAdbBridgeAuth {
    private const val PREFS = "dwas_eq_wired_adb_bridge_auth"
    private const val KEY_TOKEN = "token"
    private const val KEY_PORT = "port"
    private const val MIN_TOKEN_LENGTH = 16
    private const val MIN_PORT = 1024
    private const val MAX_PORT = 65535

    data class Endpoint(val port: Int, val token: String)

    fun parseEndpointArg(value: String?): Endpoint {
        require(!value.isNullOrBlank()) { "Missing wired ADB endpoint" }
        val separator = value.indexOf(':')
        require(separator > 0 && separator < value.lastIndex) { "Expected port:token endpoint" }
        val port = value.substring(0, separator).toInt()
        val token = value.substring(separator + 1)
        require(port in MIN_PORT..MAX_PORT) { "Wired ADB port is out of range" }
        requireValidToken(token)
        return Endpoint(port, token)
    }

    fun arm(context: Context, port: Int, token: String) {
        require(port in MIN_PORT..MAX_PORT) { "Wired ADB port is out of range" }
        requireValidToken(token)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_PORT, port)
            .commit()
    }

    fun loadEndpoint(context: Context): Endpoint? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val port = prefs.getInt(KEY_PORT, -1)
        if (port !in MIN_PORT..MAX_PORT || token.length < MIN_TOKEN_LENGTH) return null
        return Endpoint(port, token)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteSharedPreferences(PREFS)
    }

    private fun requireValidToken(token: String) {
        require(token.length >= MIN_TOKEN_LENGTH) { "Wired ADB token is too short" }
        require(token.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Wired ADB token has invalid characters" }
    }
}
