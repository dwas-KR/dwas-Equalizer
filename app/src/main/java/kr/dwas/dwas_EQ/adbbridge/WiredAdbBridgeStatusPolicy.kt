package kr.dwas.dwas_EQ.adbbridge

object WiredAdbBridgeStatusPolicy {
    private const val CONNECTED_PREFIX = "Connected to wired ADB loopback bridge"

    fun isReady(status: String): Boolean = status.startsWith(CONNECTED_PREFIX)

    fun shouldShowPermissionNotice(status: String, directAvailable: Boolean, directHasControl: Boolean): Boolean =
        !isReady(status) && !(directAvailable && directHasControl)
}
