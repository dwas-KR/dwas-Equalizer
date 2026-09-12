package kr.dwas.dwas_EQ.adbbridge

import android.os.Looper
import android.os.Process
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

object WiredAdbBridgeMain {
    private const val TAG = "dwas_EQ_WiredADB"

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            HiddenApiBypass.setHiddenApiExemptions("")
            check(Process.myUid() == SHELL_UID || Process.myUid() == ROOT_UID) {
                "Bridge must run as shell/root; uid=${Process.myUid()}"
            }
            val token = parseToken(args)
            val port = parsePort(args)
            prepareMainLooper()
            val service = WiredAdbDolbyService()
            val server = WiredAdbSocketServer.bind(port, token, service)
            Log.i(TAG, "bridge started uid=${Process.myUid()} pid=${Process.myPid()} loopback=127.0.0.1:${server.port}")

            Thread(
                {
                    try {
                        server.runLoop()
                    } catch (t: Throwable) {
                        Log.e(TAG, "loopback server failed", t)
                        kotlin.system.exitProcess(3)
                    }
                },
                "dwas-eq-adb-loopback",
            ).apply {
                isDaemon = true
                start()
            }

            Looper.loop()
        } catch (t: Throwable) {
            Log.e(TAG, "bridge fatal error", t)
            t.printStackTrace()
            kotlin.system.exitProcess(2)
        }
    }

    private fun prepareMainLooper() {
        if (Looper.myLooper() == null) {
            Looper.prepareMainLooper()
        }
    }

    private fun parseToken(args: Array<String>): String {
        val token = args.firstOrNull { it.startsWith("--token=") }?.substringAfter("--token=")
        require(!token.isNullOrBlank()) { "Missing --token argument" }
        require(token.length >= 16) { "Wired ADB token is too short" }
        return token
    }

    private fun parsePort(args: Array<String>): Int {
        val port = args.firstOrNull { it.startsWith("--port=") }?.substringAfter("--port=")?.toIntOrNull()
            ?: error("Missing or invalid --port argument")
        require(port in 1024..65535) { "Wired ADB port is out of range" }
        return port
    }

    private const val ROOT_UID = 0
    private const val SHELL_UID = 2000
}
