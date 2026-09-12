package kr.dwas.dwas_EQ.adbbridge

import android.content.Context
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

class WiredAdbSocketClient(context: Context) {
    private val appContext = context.applicationContext

    fun request(operation: String, payload: String? = null): String {
        val endpoint = WiredAdbBridgeAuth.loadEndpoint(appContext)
            ?: error("Wired ADB endpoint is not armed. Run tools\\dwas_EQ_ADB_Enable.bat first.")
        return try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = READ_TIMEOUT_MS
                socket.connect(InetSocketAddress(LOOPBACK, endpoint.port), CONNECT_TIMEOUT_MS)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                writer.write(endpoint.token)
                writer.newLine()
                writer.write(WiredAdbWireProtocol.commandLine(operation, payload))
                writer.newLine()
                writer.flush()
                val response = reader.readLine() ?: error("Wired ADB bridge closed without a response")
                require(response.length <= MAX_RESPONSE_CHARS) { "Wired ADB bridge response is too large" }
                WiredAdbBridgeRegistry.markConnected(endpoint.port)
                response
            }
        } catch (t: Throwable) {
            WiredAdbBridgeRegistry.markDisconnected("${t::class.simpleName}: ${t.message ?: "loopback bridge unavailable"}")
            throw t
        }
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 1_200
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_RESPONSE_CHARS = 65_536
    }
}
