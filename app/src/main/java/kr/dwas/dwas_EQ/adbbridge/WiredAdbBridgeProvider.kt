package kr.dwas.dwas_EQ.adbbridge

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import org.json.JSONObject

class WiredAdbBridgeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val callerUid = Binder.getCallingUid()
        checkTrustedCaller(callerUid)
        val appContext = requireNotNull(context?.applicationContext) { "Provider context unavailable" }
        return when (method) {
            METHOD_ARM -> {
                val endpoint = WiredAdbBridgeAuth.parseEndpointArg(arg)
                WiredAdbBridgeAuth.arm(appContext, endpoint.port, endpoint.token)
                WiredAdbBridgeRegistry.markArmed(endpoint.port)
                Bundle().apply { putBoolean(EXTRA_OK, true) }
            }
            METHOD_PING -> {
                val response = WiredAdbSocketClient(appContext).request("info")
                val json = JSONObject(response)
                Bundle().apply {
                    putBoolean(EXTRA_OK, json.optBoolean("ok", false))
                    putString(EXTRA_MESSAGE, json.optString("message", "Wired ADB bridge ping"))
                }
            }
            METHOD_CLEAR -> {
                WiredAdbBridgeAuth.clear(appContext)
                WiredAdbBridgeRegistry.markDisconnected()
                Bundle().apply { putBoolean(EXTRA_OK, true) }
            }
            else -> super.call(method, arg, extras)
        }
    }

    private fun checkTrustedCaller(uid: Int) {
        if (uid != SHELL_UID && uid != ROOT_UID) {
            throw SecurityException("Wired ADB bridge arming is restricted to shell/root; callerUid=$uid")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "kr.dwas.dwas_EQ.wired_adb_bridge"
        const val METHOD_ARM = "arm"
        const val METHOD_PING = "ping"
        const val METHOD_CLEAR = "clear"
        const val EXTRA_OK = "ok"
        const val EXTRA_MESSAGE = "message"
        private const val ROOT_UID = 0
        private const val SHELL_UID = 2000
    }
}
