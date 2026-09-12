package kr.dwas.dwas_EQ.adbbridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WiredAdbBridgeRegistry {
    private const val DISCONNECTED = "Not connected. Run tools\\dwas_EQ_ADB_Enable.bat over authorized USB ADB."
    private val _status = MutableStateFlow(DISCONNECTED)
    val status: StateFlow<String> = _status.asStateFlow()

    fun markConnected(port: Int) {
        _status.value = "Connected to wired ADB loopback bridge (127.0.0.1:$port)"
    }

    fun markDisconnected(reason: String? = null) {
        _status.value = if (reason.isNullOrBlank()) DISCONNECTED else "$DISCONNECTED $reason"
    }

    fun markArmed(port: Int) {
        _status.value = "Wired ADB endpoint armed on 127.0.0.1:$port; waiting for bridge response."
    }

    fun statusText(): String = _status.value
}
