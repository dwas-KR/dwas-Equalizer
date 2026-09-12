package kr.dwas.dwas_EQ.backend

import kr.dwas.dwas_EQ.core.SafeProbeEngine
import kr.dwas.dwas_EQ.persistence.PersistentDolbyRuntime

class DirectDolbyBackend : DolbyEqBackend {
    override val kind = BackendKind.DIRECT_DOLBY
    override val supportsSafeProbe = true

    override fun probe(): BackendProbe {
        if (PersistentDolbyRuntime.isHoldingSession()) {
            val validation = PersistentDolbyRuntime.validateHeld()
            return BackendProbe(
                kind,
                available = validation.holding,
                hasControl = validation.hasControl,
                gainCount = validation.gainCount,
                details = validation.message,
            )
        }

        val descriptor = RawAudioEffectSession.queryDapDescriptor()
            ?: return BackendProbe(kind, false, details = "DAP implementation UUID not listed by AudioEffect.queryEffects().")
        return try {
            RawAudioEffectSession.open().use { session ->
                val gains = session.readGains()
                BackendProbe(
                    kind,
                    gains.size == 20,
                    session.hasControl,
                    gains.size,
                    "DAP ${descriptor.name}; implementor=${descriptor.implementor}; session=0; read=${gains.size} gains",
                )
            }
        } catch (t: Throwable) {
            BackendProbe(kind, false, details = "${t::class.simpleName}: ${t.message ?: "Direct DAP access failed"}")
        }
    }

    override fun readGains(): BackendResult<IntArray> {
        PersistentDolbyRuntime.readHeld()?.let {
            return BackendResult(true, it, "Read 20 Dolby DAP gains from the persistent Direct session.")
        }
        return try {
            RawAudioEffectSession.open().use { s ->
                if (!s.hasControl) return BackendResult(false, message = "Direct DAP session opened but hasControl() is false.")
                BackendResult(true, s.readGains(), "Read 20 Dolby DAP gains directly.")
            }
        } catch (t: Throwable) {
            BackendResult(false, message = "${t::class.simpleName}: ${t.message ?: "Direct read failed"}")
        }
    }

    override fun readGeqEnabled(): BackendResult<Boolean> {
        PersistentDolbyRuntime.readHeldEnabled()?.let {
            return BackendResult(true, it, "Read Dolby GEQ enabled state from the persistent Direct session.")
        }
        return try {
            RawAudioEffectSession.open().use { s ->
                if (!s.hasControl) return BackendResult(false, message = "Direct DAP session opened but hasControl() is false.")
                BackendResult(true, s.readGeqEnabled(), "Read Dolby GEQ enabled state directly.")
            }
        } catch (t: Throwable) {
            BackendResult(false, message = "${t::class.simpleName}: ${t.message ?: "Direct GEQ enable read failed"}")
        }
    }

    override fun writeGeqEnabled(enabled: Boolean): BackendResult<Unit> {
        if (PersistentDolbyRuntime.isHoldingSession()) {
            return if (PersistentDolbyRuntime.writeHeldEnabledAndVerify(enabled)) {
                BackendResult(true, Unit, "Persistent Direct Dolby GEQ enabled state verified.")
            } else {
                BackendResult(false, message = "Persistent Direct Dolby GEQ enabled state write failed.")
            }
        }
        return try {
            RawAudioEffectSession.open().use { s ->
                if (!s.hasControl) return BackendResult(false, message = "Direct DAP has no control; no GEQ enable write performed.")
                s.writeGeqEnabled(enabled)
                if (s.readGeqEnabled() == enabled) BackendResult(true, Unit, "Direct Dolby GEQ enabled state verified.")
                else BackendResult(false, message = "Direct Dolby GEQ enabled state readback differs.")
            }
        } catch (t: Throwable) {
            BackendResult(false, message = "${t::class.simpleName}: ${t.message ?: "Direct GEQ enable write failed"}")
        }
    }

    override fun writeGains(gains: IntArray): BackendResult<Unit> {
        if (PersistentDolbyRuntime.isHoldingSession()) {
            return if (PersistentDolbyRuntime.writeHeldAndVerify(gains)) {
                BackendResult(true, Unit, "Persistent Direct Dolby write/readback verified; session retained.")
            } else {
                BackendResult(false, message = "Persistent Direct Dolby write/readback failed.")
            }
        }
        return try {
            RawAudioEffectSession.open().use { s ->
                if (!s.hasControl) return BackendResult(false, message = "Direct DAP has no control; no write performed.")
                s.writeGains(gains)
                val readback = s.readGains()
                if (readback.contentEquals(gains)) BackendResult(true, Unit, "Direct Dolby write/readback verified.")
                else BackendResult(false, message = "Direct Dolby readback differs; write not verified.")
            }
        } catch (t: Throwable) {
            BackendResult(false, message = "${t::class.simpleName}: ${t.message ?: "Direct write failed"}")
        }
    }

    fun safeProbe(): BackendResult<Unit> {
        if (PersistentDolbyRuntime.isHoldingSession()) {
            val result = PersistentDolbyRuntime.safeProbeHeld()
                ?: return BackendResult(false, message = "Persistent Direct Dolby session is no longer controllable.")
            return if (result.writeVerified && result.restoreVerified) {
                BackendResult(true, Unit, "Persistent Direct safe probe verified; held session retained and original gains restored.")
            } else {
                BackendResult(false, message = "Persistent Direct safe probe write=${result.writeVerified}, restore=${result.restoreVerified}, error=${result.error ?: "none"}")
            }
        }

        return try {
            RawAudioEffectSession.open().use { session ->
                if (!session.hasControl) return BackendResult(false, message = "Direct DAP hasControl() is false.")
                val r = SafeProbeEngine.run(session)
                if (r.writeVerified && r.restoreVerified) BackendResult(true, Unit, "Direct safe probe verified; original gains restored.")
                else BackendResult(false, message = "Direct safe probe write=${r.writeVerified}, restore=${r.restoreVerified}, error=${r.error ?: "none"}")
            }
        } catch (t: Throwable) {
            BackendResult(false, message = "Direct safe probe failed: ${t.message}")
        }
    }

    override fun close() {
        
    }
}
