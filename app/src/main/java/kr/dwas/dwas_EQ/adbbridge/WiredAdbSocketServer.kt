package kr.dwas.dwas_EQ.adbbridge

import kr.dwas.dwas_EQ.bridge.BridgeJson
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.concurrent.thread

class WiredAdbSocketServer private constructor(
    private val serverSocket: ServerSocket,
    private val token: String,
    private val service: WiredAdbDolbyService,
) : AutoCloseable {

    val port: Int get() = serverSocket.localPort

    fun runLoop() {
        while (!serverSocket.isClosed) {
            val socket = serverSocket.accept()
            thread(name = "dwas-eq-adb-client", isDaemon = true) {
                handleClient(socket)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use {
            it.soTimeout = CLIENT_TIMEOUT_MS
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream(), Charsets.UTF_8))
            val suppliedToken = reader.readLine()?.takeIf { value -> value.length <= MAX_TOKEN_CHARS }
            if (!constantTimeEquals(token, suppliedToken)) {
                writer.write(BridgeJson.error("Wired ADB bridge authentication failed"))
                writer.newLine()
                writer.flush()
                return
            }
            val line = reader.readLine()?.takeIf { value -> value.length <= MAX_COMMAND_CHARS }
                ?: run {
                    writer.write(BridgeJson.error("Missing wired ADB bridge command"))
                    writer.newLine()
                    writer.flush()
                    return
                }
            val response = runCatching { dispatch(WiredAdbWireProtocol.parseCommand(line)) }
                .getOrElse { BridgeJson.error("${it::class.simpleName}: ${it.message ?: "wired ADB command failed"}") }
            writer.write(response)
            writer.newLine()
            writer.flush()
        }
    }

    private fun dispatch(command: WiredAdbWireProtocol.Command): String = when (command.name) {
        WiredAdbWireProtocol.COMMAND_PROBE -> service.probe()
        WiredAdbWireProtocol.COMMAND_READ -> service.readGains()
        WiredAdbWireProtocol.COMMAND_WRITE -> service.writeGains(requireNotNull(command.payload) { "WRITE requires gains payload" })
        WiredAdbWireProtocol.COMMAND_SAFE_PROBE -> service.safeProbe()
        WiredAdbWireProtocol.COMMAND_READ_GEQ_ENABLED -> service.readGeqEnabled()
        WiredAdbWireProtocol.COMMAND_WRITE_GEQ_ENABLED -> service.writeGeqEnabled(requireNotNull(command.payload) { "WRITE_GEQ_ENABLED requires boolean payload" })
        WiredAdbWireProtocol.COMMAND_INFO -> service.bridgeInfo()
        WiredAdbWireProtocol.COMMAND_MEDIA_SESSIONS -> service.mediaSessions()
        WiredAdbWireProtocol.COMMAND_SESSION_FX_APPLY -> service.sessionFxApply(requireNotNull(command.payload) { "SESSION_FX_APPLY requires settings payload" })
        WiredAdbWireProtocol.COMMAND_SESSION_FX_RELEASE -> service.sessionFxRelease()
        WiredAdbWireProtocol.COMMAND_CONTROL_RESET -> service.controlReset()
        else -> BridgeJson.error("Unsupported wired ADB command: ${command.name}")
    }

    override fun close() {
        runCatching { serverSocket.close() }
    }

    companion object {
        private const val CLIENT_TIMEOUT_MS = 6_000
        private const val MAX_TOKEN_CHARS = 128
        private const val MAX_COMMAND_CHARS = 8_192

        fun bind(port: Int, token: String, service: WiredAdbDolbyService): WiredAdbSocketServer {
            val socket = ServerSocket()
            try {
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 8)
                return WiredAdbSocketServer(socket, token, service)
            } catch (t: Throwable) {
                runCatching { socket.close() }
                throw t
            }
        }

        private fun constantTimeEquals(expected: String, actual: String?): Boolean {
            if (actual == null) return false
            return MessageDigest.isEqual(
                expected.toByteArray(StandardCharsets.UTF_8),
                actual.toByteArray(StandardCharsets.UTF_8),
            )
        }
    }
}
