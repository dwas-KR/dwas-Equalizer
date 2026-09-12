package kr.dwas.dwas_EQ.backend

data class BackendApplyOutcome(
    val result: BackendResult<BackendKind>,
    val baseline: IntArray? = null,
    val baselineEnabled: Boolean? = null,
)

object BackendApplyEngine {
    fun apply(
        candidates: List<EqBackend>,
        target: IntArray,
        gate: (EqBackend) -> String?,
    ): BackendApplyOutcome {
        require(target.size == 20) { "Expected 20 EQ gains" }
        if (candidates.isEmpty()) {
            return BackendApplyOutcome(BackendResult(false, message = "No backend candidates are enabled."))
        }

        val attempts = mutableListOf<String>()
        for (backend in candidates) {
            val probe = try {
                backend.probe()
            } catch (t: Throwable) {
                attempts += "${backend.kind.label}: probe exception ${t::class.simpleName}: ${t.message ?: "unknown"}"
                continue
            }
            if (!probe.available || !probe.hasControl) {
                attempts += "${backend.kind.label}: ${probe.details}"
                continue
            }

            val gateReason = gate(backend)
            if (gateReason != null) {
                attempts += "${backend.kind.label}: $gateReason"
                continue
            }

            val before = try {
                backend.readGains()
            } catch (t: Throwable) {
                BackendResult(false, message = "read exception ${t::class.simpleName}: ${t.message ?: "unknown"}")
            }
            val original = before.value?.takeIf { before.ok && it.size == 20 }?.copyOf()
            if (original == null) {
                attempts += "${backend.kind.label}: original gains could not be read safely (${before.message})"
                continue
            }

            val originalEnabled = if (backend is DolbyEqBackend) {
                val state = try {
                    backend.readGeqEnabled()
                } catch (t: Throwable) {
                    BackendResult(false, message = "GEQ enable read exception ${t::class.simpleName}: ${t.message ?: "unknown"}")
                }
                if (!state.ok || state.value == null) {
                    attempts += "${backend.kind.label}: original GEQ enabled state could not be read safely (${state.message})"
                    continue
                }
                state.value
            } else null

            val write = try {
                backend.applyAndVerify(target)
            } catch (t: Throwable) {
                BackendResult(false, message = "write exception ${t::class.simpleName}: ${t.message ?: "unknown"}")
            }
            if (write.ok) {
                return BackendApplyOutcome(
                    result = BackendResult(true, backend.kind, "${backend.kind.label}: ${write.message}"),
                    baseline = original,
                    baselineEnabled = originalEnabled,
                )
            }

            val rollback = try {
                if (backend is DolbyEqBackend && originalEnabled != null) backend.restoreAndVerify(original, originalEnabled)
                else backend.restoreAndVerify(original)
            } catch (t: Throwable) {
                BackendResult(false, message = "rollback exception ${t::class.simpleName}: ${t.message ?: "unknown"}")
            }
            attempts += buildString {
                append("${backend.kind.label}: ${write.message}; rollback=")
                append(if (rollback.ok) "verified" else "unverified (${rollback.message})")
            }
        }

        return BackendApplyOutcome(
            BackendResult(false, message = "No backend applied the EQ. ${attempts.joinToString(" | ")}")
        )
    }
}
