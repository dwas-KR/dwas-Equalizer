package kr.dwas.dwas_EQ.core

interface GainTransport {
    fun readGains(): IntArray
    fun writeGains(gains: IntArray)
}

data class SafeProbeResult(
    val original: IntArray?,
    val probeValues: IntArray?,
    val readback: IntArray?,
    val restoredReadback: IntArray?,
    val deltaApplied: Int,
    val writeVerified: Boolean,
    val restoreAttempted: Boolean,
    val restoreVerified: Boolean,
    val error: String? = null,
    val restoreError: String? = null,
)

object SafeProbeEngine {
    private const val RESTORE_READ_ATTEMPTS = 3

    fun run(transport: GainTransport): SafeProbeResult {
        var original: IntArray? = null
        var probe: IntArray? = null
        var readback: IntArray? = null
        var restored: IntArray? = null
        var writeVerified = false
        var restoreAttempted = false
        var restoreVerified = false
        var error: String? = null
        var restoreError: String? = null
        var delta = 8

        try {
            original = transport.readGains().also { require(it.size == EqSpec.DAP_GAIN_COUNT) }
            probe = original.copyOf()
            delta = if (probe[0] <= EqSpec.DAP_MAX_GAIN - 8) 8 else -8
            probe[0] = (probe[0] + delta).coerceIn(EqSpec.DAP_MIN_GAIN, EqSpec.DAP_MAX_GAIN)
            transport.writeGains(probe)
            readback = transport.readGains()
            writeVerified = readback.contentEquals(probe)
        } catch (t: Throwable) {
            error = "${t::class.simpleName}: ${t.message ?: "unknown error"}"
        } finally {
            if (original != null) {
                restoreAttempted = true
                try {
                    transport.writeGains(original)
                    for (attempt in 0 until RESTORE_READ_ATTEMPTS) {
                        restored = transport.readGains()
                        if (restored.contentEquals(original)) {
                            restoreVerified = true
                            break
                        }
                    }
                } catch (t: Throwable) {
                    restoreError = "${t::class.simpleName}: ${t.message ?: "unknown restore error"}"
                }
            }
        }

        return SafeProbeResult(
            original = original,
            probeValues = probe,
            readback = readback,
            restoredReadback = restored,
            deltaApplied = delta,
            writeVerified = writeVerified,
            restoreAttempted = restoreAttempted,
            restoreVerified = restoreVerified,
            error = error,
            restoreError = restoreError,
        )
    }
}
