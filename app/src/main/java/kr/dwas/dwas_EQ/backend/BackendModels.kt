package kr.dwas.dwas_EQ.backend

enum class BackendKind(val label: String) {
    WIRED_ADB_DOLBY("Wired ADB Dolby"),
    DIRECT_DOLBY("Direct Dolby"),
    ROOT_DOLBY("Root Dolby"),
    ANDROID_EQUALIZER("Android Equalizer"),
}

enum class BackendPreference(val label: String) {
    AUTO("Auto"), WIRED_ADB("Wired ADB"), DIRECT("Direct"), ROOT("Root"), ANDROID_EQ("Android EQ")
}

data class BackendProbe(
    val backend: BackendKind,
    val available: Boolean,
    val hasControl: Boolean = false,
    val gainCount: Int? = null,
    val details: String,
)

data class BackendResult<T>(
    val ok: Boolean,
    val value: T? = null,
    val message: String,
)

interface EqBackend {
    val kind: BackendKind
    val supportsSafeProbe: Boolean
    fun probe(): BackendProbe
    fun readGains(): BackendResult<IntArray>
    fun writeGains(gains: IntArray): BackendResult<Unit>

    fun applyAndVerify(gains: IntArray): BackendResult<Unit> {
        val write = writeGains(gains)
        if (!write.ok) return write
        val readback = readGains()
        val verified = readback.ok && readback.value?.contentEquals(gains) == true
        return if (verified) BackendResult(true, Unit, "${write.message}; readback verified")
        else BackendResult(false, message = "${write.message}; readback verification failed: ${readback.message}")
    }

    fun restoreAndVerify(gains: IntArray): BackendResult<Unit> = applyAndVerify(gains)
    fun close() {}
}

interface DolbyEqBackend : EqBackend {
    fun readGeqEnabled(): BackendResult<Boolean>
    fun writeGeqEnabled(enabled: Boolean): BackendResult<Unit>

    override fun applyAndVerify(gains: IntArray): BackendResult<Unit> {
        val enabled = writeGeqEnabled(true)
        if (!enabled.ok) return enabled
        val write = writeGains(gains)
        if (!write.ok) return write
        val enabledReadback = readGeqEnabled()
        val gainsReadback = readGains()
        val verified = enabledReadback.ok && enabledReadback.value == true &&
            gainsReadback.ok && gainsReadback.value?.contentEquals(gains) == true
        return if (verified) BackendResult(true, Unit, "${write.message}; GEQ enable and gain readback verified")
        else BackendResult(false, message = "${write.message}; GEQ enable/gain verification failed")
    }

    fun restoreAndVerify(gains: IntArray, enabled: Boolean): BackendResult<Unit> {
        val enableForWrite = writeGeqEnabled(true)
        if (!enableForWrite.ok) return enableForWrite
        val write = writeGains(gains)
        if (!write.ok) return write
        val restoreEnabled = writeGeqEnabled(enabled)
        if (!restoreEnabled.ok) return restoreEnabled
        val enabledReadback = readGeqEnabled()
        val gainsReadback = readGains()
        val verified = enabledReadback.ok && enabledReadback.value == enabled &&
            gainsReadback.ok && gainsReadback.value?.contentEquals(gains) == true
        return if (verified) BackendResult(true, Unit, "Dolby GEQ baseline gains and enabled state restored")
        else BackendResult(false, message = "Dolby GEQ baseline state verification failed")
    }
}
