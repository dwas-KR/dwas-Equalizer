package kr.dwas.dwas_EQ.safety

object HearingSafetyPolicy {
    const val CURRENT_VERSION = 1

    fun isAccepted(acceptedVersion: Int): Boolean = acceptedVersion >= CURRENT_VERSION

    fun shouldRequireConsent(
        notificationPermissionGranted: Boolean,
        preferencesReady: Boolean,
        acceptedVersion: Int,
    ): Boolean = notificationPermissionGranted && preferencesReady && !isAccepted(acceptedVersion)
}
