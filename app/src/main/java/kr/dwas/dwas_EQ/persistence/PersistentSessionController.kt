package kr.dwas.dwas_EQ.persistence

import kr.dwas.dwas_EQ.core.GainTransport
import kr.dwas.dwas_EQ.core.SafeProbeEngine
import kr.dwas.dwas_EQ.core.SafeProbeResult

interface PersistentGainSession : GainTransport, AutoCloseable {
    val hasControl: Boolean
    fun readGeqEnabled(): Boolean
    fun writeGeqEnabled(enabled: Boolean)
}

data class ActiveEqTransaction(
    val baseline: IntArray,
    val baselineEnabled: Boolean,
    val appliedTarget: IntArray,
) {
    fun snapshot(): ActiveEqTransaction = ActiveEqTransaction(baseline.copyOf(), baselineEnabled, appliedTarget.copyOf())
}

data class PersistentSessionApplyResult(
    val ok: Boolean,
    val hasControl: Boolean,
    val baseline: IntArray? = null,
    val baselineEnabled: Boolean? = null,
    val message: String,
)

data class PersistentSessionValidation(
    val holding: Boolean,
    val hasControl: Boolean,
    val gainCount: Int? = null,
    val message: String,
)

class PersistentSessionController(
    private val sessionFactory: () -> PersistentGainSession,
) : AutoCloseable {
    private var heldSession: PersistentGainSession? = null
    private var transaction: ActiveEqTransaction? = null

    @Synchronized
    fun applyAndHold(target: IntArray): PersistentSessionApplyResult = applyAndHold(target, null, null)

    @Synchronized
    fun applyAndHold(target: IntArray, baselineOverride: IntArray?): PersistentSessionApplyResult =
        applyAndHold(target, baselineOverride, null)

    @Synchronized
    fun applyAndHold(
        target: IntArray,
        baselineOverride: IntArray?,
        baselineEnabledOverride: Boolean?,
    ): PersistentSessionApplyResult {
        require(target.size == 20) { "Expected 20 Dolby DAP gains" }
        baselineOverride?.let { require(it.size == 20) { "Expected 20 Dolby DAP baseline gains" } }

        var session = heldSession
        if (session != null && !session.hasControl) {
            releaseSessionLocked()
            session = null
        }

        if (session == null) {
            val opened = try {
                sessionFactory()
            } catch (t: Throwable) {
                return PersistentSessionApplyResult(false, false, message = errorMessage(t, "Direct Dolby session open failed"))
            }
            if (!opened.hasControl) {
                runCatching { opened.close() }
                return PersistentSessionApplyResult(false, false, message = "Direct Dolby opened but hasControl() is false.")
            }
            session = opened
        }

        val activeSession = requireNotNull(session)
        val existing = transaction?.snapshot()
        var baseline = existing?.baseline?.copyOf() ?: baselineOverride?.copyOf()
        var baselineEnabled = existing?.baselineEnabled ?: baselineEnabledOverride
        var beforeAttempt: IntArray? = null
        var beforeAttemptEnabled: Boolean? = null
        var writeAttempted = false

        return try {
            val current = activeSession.readGains().also {
                check(it.size == 20) { "Expected 20 Dolby DAP gains, got ${it.size}" }
            }.copyOf()
            val currentEnabled = activeSession.readGeqEnabled()
            beforeAttempt = current.copyOf()
            beforeAttemptEnabled = currentEnabled
            if (baseline == null) baseline = current.copyOf()
            if (baselineEnabled == null) baselineEnabled = currentEnabled

            if (!currentEnabled || !current.contentEquals(target)) {
                writeAttempted = true
                activeSession.writeGeqEnabled(true)
                if (!current.contentEquals(target)) activeSession.writeGains(target)
                check(activeSession.readGeqEnabled()) { "Direct Dolby GEQ enable readback=false." }
                val readback = activeSession.readGains()
                check(readback.contentEquals(target)) { "Direct Dolby readback differs from the requested curve." }
            }

            val stableBaseline = requireNotNull(baseline).copyOf()
            val stableBaselineEnabled = requireNotNull(baselineEnabled)
            heldSession = activeSession
            transaction = ActiveEqTransaction(stableBaseline, stableBaselineEnabled, target.copyOf())
            PersistentSessionApplyResult(
                ok = true,
                hasControl = true,
                baseline = stableBaseline.copyOf(),
                baselineEnabled = stableBaselineEnabled,
                message = "Direct Dolby GEQ enable and gain write/readback verified; session retained by the foreground service.",
            )
        } catch (t: Throwable) {
            val rollbackTarget = beforeAttempt?.copyOf()
            val rollbackEnabled = beforeAttemptEnabled
            val rollbackVerified = if (!writeAttempted) {
                true
            } else {
                rollbackTarget != null && rollbackEnabled != null && verifyState(activeSession, rollbackTarget, rollbackEnabled)
            }
            runCatching { activeSession.close() }
            if (heldSession === activeSession) heldSession = null
            transaction = if (rollbackVerified && existing != null) existing.snapshot() else null
            PersistentSessionApplyResult(
                ok = false,
                hasControl = runCatching { activeSession.hasControl }.getOrDefault(false),
                baseline = baseline?.copyOf(),
                baselineEnabled = baselineEnabled,
                message = buildString {
                    append(errorMessage(t, "Persistent Direct Dolby apply failed"))
                    append("; rollback=")
                    append(
                        when {
                            !writeAttempted -> "not needed; session released"
                            rollbackVerified -> "verified to pre-attempt state; session released"
                            else -> "unverified; session released and transaction discarded"
                        }
                    )
                },
            )
        }
    }

    @Synchronized
    fun activeTransaction(): ActiveEqTransaction? = transaction?.snapshot()

    @Synchronized
    fun isHoldingSession(): Boolean = heldSession != null && transaction != null

    @Synchronized
    fun readHeld(): IntArray? {
        val session = heldSession ?: return null
        if (!session.hasControl) {
            releaseSessionLocked()
            return null
        }
        return try {
            session.readGains()
        } catch (_: Throwable) {
            releaseSessionLocked()
            null
        }
    }

    @Synchronized
    fun readHeldEnabled(): Boolean? {
        val session = heldSession ?: return null
        if (!session.hasControl) {
            releaseSessionLocked()
            return null
        }
        return try {
            session.readGeqEnabled()
        } catch (_: Throwable) {
            releaseSessionLocked()
            null
        }
    }

    @Synchronized
    fun writeHeldEnabledAndVerify(enabled: Boolean): Boolean {
        val session = heldSession ?: return false
        if (!session.hasControl) {
            releaseSessionLocked()
            return false
        }
        return runCatching {
            session.writeGeqEnabled(enabled)
            session.readGeqEnabled() == enabled
        }.getOrElse {
            releaseSessionLocked()
            false
        }
    }

    @Synchronized
    fun validateHeld(): PersistentSessionValidation {
        val session = heldSession
            ?: return PersistentSessionValidation(false, false, message = "No persistent Direct Dolby session is held. Press Apply to reacquire it.")
        if (!session.hasControl) {
            releaseSessionLocked()
            return PersistentSessionValidation(false, false, message = "Persistent Direct Dolby lost control. Press Apply to reacquire it.")
        }
        return try {
            val gains = session.readGains()
            val enabled = session.readGeqEnabled()
            if (gains.size != 20) {
                releaseSessionLocked()
                PersistentSessionValidation(false, false, gains.size, "Persistent Direct Dolby returned an invalid gain count; session released.")
            } else {
                PersistentSessionValidation(true, true, gains.size, "Persistent Direct Dolby session is alive; GEQ enabled=$enabled; gains readable.")
            }
        } catch (t: Throwable) {
            releaseSessionLocked()
            PersistentSessionValidation(false, false, message = "Persistent Direct Dolby validation failed: ${t.message ?: t::class.simpleName}")
        }
    }

    @Synchronized
    fun safeProbeHeld(): SafeProbeResult? {
        val session = heldSession ?: return null
        if (!session.hasControl) {
            releaseSessionLocked()
            return null
        }
        return try {
            SafeProbeEngine.run(session).also { result ->
                if (!result.writeVerified || !result.restoreVerified) releaseSessionLocked()
            }
        } catch (_: Throwable) {
            releaseSessionLocked()
            null
        }
    }

    @Synchronized
    fun writeHeldAndVerify(target: IntArray): Boolean {
        require(target.size == 20)
        val session = heldSession ?: return false
        val currentTransaction = transaction?.snapshot() ?: return false
        if (!session.hasControl) {
            releaseSessionLocked()
            return false
        }

        val beforeAttempt = try {
            session.readGains().also { check(it.size == 20) }.copyOf()
        } catch (_: Throwable) {
            releaseSessionLocked()
            return false
        }
        val beforeAttemptEnabled = try {
            session.readGeqEnabled()
        } catch (_: Throwable) {
            releaseSessionLocked()
            return false
        }

        return try {
            session.writeGeqEnabled(true)
            session.writeGains(target)
            val verified = session.readGeqEnabled() && session.readGains().contentEquals(target)
            if (verified) {
                transaction = ActiveEqTransaction(currentTransaction.baseline.copyOf(), currentTransaction.baselineEnabled, target.copyOf())
                true
            } else {
                val rollbackVerified = verifyState(session, beforeAttempt, beforeAttemptEnabled)
                releaseSessionLocked()
                transaction = if (rollbackVerified) currentTransaction.snapshot() else null
                false
            }
        } catch (_: Throwable) {
            val rollbackVerified = verifyState(session, beforeAttempt, beforeAttemptEnabled)
            releaseSessionLocked()
            transaction = if (rollbackVerified) currentTransaction.snapshot() else null
            false
        }
    }

    @Synchronized
    fun restoreAndRelease(): Boolean {
        val active = transaction ?: return false
        val current = heldSession
        val restoreSession: PersistentGainSession = if (current != null && current.hasControl) {
            current
        } else {
            releaseSessionLocked()
            val opened = try { sessionFactory() } catch (_: Throwable) { return false }
            if (!opened.hasControl) {
                runCatching { opened.close() }
                return false
            }
            heldSession = opened
            opened
        }
        val ok = verifyState(restoreSession, active.baseline, active.baselineEnabled)
        if (ok) releaseLocked() else releaseSessionLocked()
        return ok
    }

    @Synchronized
    fun releaseSession() = releaseSessionLocked()

    @Synchronized
    fun release() = releaseLocked()

    private fun verifyState(session: PersistentGainSession, gains: IntArray, enabled: Boolean): Boolean = runCatching {
        session.writeGeqEnabled(true)
        session.writeGains(gains)
        session.writeGeqEnabled(enabled)
        session.readGains().contentEquals(gains) && session.readGeqEnabled() == enabled
    }.getOrDefault(false)

    private fun releaseSessionLocked() {
        val old = heldSession
        heldSession = null
        if (old != null) runCatching { old.close() }
    }

    private fun releaseLocked() {
        releaseSessionLocked()
        transaction = null
    }

    private fun errorMessage(t: Throwable, fallback: String): String =
        "${t::class.simpleName}: ${t.message ?: fallback}"

    override fun close() = release()
}
