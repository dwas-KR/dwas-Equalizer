package kr.dwas.dwas_EQ

import android.content.Context
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeAuth
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeRegistry
import kr.dwas.dwas_EQ.adbbridge.WiredAdbBridgeStatusPolicy
import kr.dwas.dwas_EQ.adbbridge.WiredAdbSocketClient
import kr.dwas.dwas_EQ.audio.AudioRouteMonitor
import kr.dwas.dwas_EQ.audio.RouteState
import kr.dwas.dwas_EQ.backend.AndroidEqualizerRuntime
import kr.dwas.dwas_EQ.backend.BackendApplyEngine
import kr.dwas.dwas_EQ.backend.BackendCoordinator
import kr.dwas.dwas_EQ.backend.BackendKind
import kr.dwas.dwas_EQ.backend.BackendPolicy
import kr.dwas.dwas_EQ.backend.BackendPreference
import kr.dwas.dwas_EQ.backend.BackendProbe
import kr.dwas.dwas_EQ.backend.BackendResult
import kr.dwas.dwas_EQ.backend.BackendStrategy
import kr.dwas.dwas_EQ.backend.BackendTransactionRuntime
import kr.dwas.dwas_EQ.backend.EqMasterToggleActivationPolicy
import kr.dwas.dwas_EQ.backend.EqBackend
import kr.dwas.dwas_EQ.backend.SafeProbePolicy
import kr.dwas.dwas_EQ.core.NineBandEqAdapter
import kr.dwas.dwas_EQ.data.AppSettings
import kr.dwas.dwas_EQ.device.DeviceDetector
import kr.dwas.dwas_EQ.device.DeviceSnapshot
import kr.dwas.dwas_EQ.domain.EqCurve
import kr.dwas.dwas_EQ.persistence.PersistentDolbyRuntime
import kr.dwas.dwas_EQ.standardfx.AudioSessionDiscovery

class EqualizerRepository(context: Context) {
    private val appContext = context.applicationContext
    val routeMonitor = AudioRouteMonitor(context)
    val device: DeviceSnapshot = DeviceDetector.snapshot(context)
    val wiredAdbStatus = WiredAdbBridgeRegistry.status

    @Volatile private var settings = AppSettings()
    private val coordinator = BackendCoordinator(context) { settings.rootOptIn }

    fun updateSettings(value: AppSettings) {
        val preference = BackendPolicy.effectivePreference(value.backendPreference, device.known?.dolbyGeqRuntimeInactive == true)
        settings = if (preference == value.backendPreference) value else value.copy(backendPreference = preference)
    }
    fun wiredAdbStatusText(): String = WiredAdbBridgeRegistry.statusText()
    fun refreshWiredAdbStatus() {
        if (settings.controlAccessReset || WiredAdbBridgeAuth.loadEndpoint(appContext) == null) return
        runCatching { WiredAdbSocketClient(appContext).request("info") }
    }
    fun verifyWiredAdbReadyAfterReset(): Boolean {
        if (!settings.controlAccessReset || WiredAdbBridgeAuth.loadEndpoint(appContext) == null) return false
        return runCatching {
            WiredAdbSocketClient(appContext).request("info")
            WiredAdbBridgeStatusPolicy.isReady(WiredAdbBridgeRegistry.statusText())
        }.getOrDefault(false)
    }
    fun probeAll(): List<BackendProbe> = if (settings.controlAccessReset) controlResetProbes() else coordinator.allProbes()
    fun allowControlAccess() { settings = settings.copy(controlAccessReset = false) }

    private fun controlResetProbes(): List<BackendProbe> = listOf(
        BackendProbe(
            backend = BackendKind.WIRED_ADB_DOLBY,
            available = false,
            hasControl = false,
            details = appContext.getString(R.string.diagnostics_control_access_reset_detail),
        ),
        BackendProbe(
            backend = BackendKind.DIRECT_DOLBY,
            available = false,
            hasControl = false,
            details = appContext.getString(R.string.diagnostics_control_access_reset_detail),
        ),
        BackendProbe(
            backend = BackendKind.ROOT_DOLBY,
            available = false,
            hasControl = false,
            details = appContext.getString(R.string.diagnostics_control_access_reset_detail),
        ),
        coordinator.androidEq.probe(),
    )

    fun currentRoute(): RouteState {
        routeMonitor.refresh()
        return routeMonitor.state.value
    }
    fun hasActiveEqTransaction(): Boolean =
        PersistentDolbyRuntime.activeTransaction() != null || BackendTransactionRuntime.get() != null || settings.androidEqArmed

    fun activeBackendKind(): BackendKind? =
        if (PersistentDolbyRuntime.activeTransaction() != null) BackendKind.DIRECT_DOLBY
        else BackendTransactionRuntime.get()?.backend ?: if (settings.androidEqArmed) BackendKind.ANDROID_EQUALIZER else null

    fun activeBaseline(): IntArray? =
        PersistentDolbyRuntime.activeTransaction()?.baseline?.copyOf()
            ?: BackendTransactionRuntime.get()?.baseline?.copyOf()

    fun shouldDeferFailedEqEnableUntilPlayback(): Boolean {
        val activeMediaSessionAvailable = AudioSessionDiscovery(appContext)
            .discover(force = true)
            .sessions
            .any { it > 0 }
        return EqMasterToggleActivationPolicy.shouldDeferAndroidEq(
            preference = settings.backendPreference,
            rootOptIn = settings.rootOptIn,
            activeMediaSessionAvailable = activeMediaSessionAvailable,
        )
    }

    private fun isDolby(kind: BackendKind) = kind != BackendKind.ANDROID_EQUALIZER

    private fun routeGateReason(kind: BackendKind, route: RouteState): String? =
        if (isDolby(kind) && !route.dolbyWriteSafeByDefault && !settings.nonSpeakerOverride) {
            "Dolby write blocked on ${route.description}; trying the next safe backend."
        } else null

    private fun persistentDirectAllowed(route: RouteState): Boolean =
        route.dolbyWriteSafeByDefault || settings.nonSpeakerOverride

    fun safeProbeAvailable(route: RouteState, probes: List<BackendProbe>): Boolean {
        if (settings.controlAccessReset) return false
        return (PersistentDolbyRuntime.isHoldingSession() && persistentDirectAllowed(route)) || SafeProbePolicy.isAvailable(
            probes = probes,
            preference = settings.backendPreference,
            rootOptIn = settings.rootOptIn,
            dolbyRouteAllowed = persistentDirectAllowed(route),
        )
    }

    fun apply(curve: EqCurve, route: RouteState): BackendResult<BackendKind> {
        if (settings.controlAccessReset) return BackendResult(false, message = appContext.getString(R.string.diagnostics_control_access_reset_detail))
        val target = NineBandEqAdapter.uiDbToDap(curve.toFloatArray())
        val attempts = mutableListOf<String>()
        var previousBackendTransaction = BackendTransactionRuntime.get()
        if (previousBackendTransaction?.backend == BackendKind.ANDROID_EQUALIZER) {
            val handoffRestore = coordinator.androidEq.restoreAndVerify(previousBackendTransaction.baseline)
            if (!handoffRestore.ok) {
                return BackendResult(false, message = "Existing Android Equalizer session could not be restored before backend reacquisition: ${handoffRestore.message}")
            }
            BackendTransactionRuntime.clear()
            previousBackendTransaction = null
        }
        val persistentTransaction = PersistentDolbyRuntime.activeTransaction()
        val preservedBaseline = persistentTransaction?.baseline?.copyOf()
            ?: previousBackendTransaction?.takeIf { isDolby(it.backend) }?.baseline?.copyOf()
        val preservedBaselineEnabled = persistentTransaction?.baselineEnabled
            ?: previousBackendTransaction?.takeIf { isDolby(it.backend) }?.baselineEnabled

        for (strategy in BackendPolicy.strategyOrder(settings.backendPreference, settings.rootOptIn)) {
            if (strategy == BackendStrategy.PERSISTENT_DIRECT) {
                if (!persistentDirectAllowed(route)) {
                    attempts += "Persistent Direct Dolby: blocked on ${route.description} by the Dolby route safety policy."
                    continue
                }
                val held = PersistentDolbyRuntime.applyFromUserButton(target, preservedBaseline, preservedBaselineEnabled)
                if (held.ok) {
                    BackendTransactionRuntime.clear()
                    return BackendResult(true, BackendKind.DIRECT_DOLBY, "Persistent Direct Dolby: ${held.message}")
                }
                attempts += "Persistent Direct Dolby: ${held.message}"
                continue
            }

            val backend = coordinator.backend(strategy.backendKind())
            if (backend.kind == BackendKind.ANDROID_EQUALIZER) {
                val activePersistent = PersistentDolbyRuntime.activeTransaction()
                if (activePersistent != null) {
                    if (!PersistentDolbyRuntime.restoreAndRelease()) {
                        attempts += "Android Equalizer fallback blocked because the active Direct Dolby baseline could not be restored."
                        continue
                    }
                }
                val activeDolby = BackendTransactionRuntime.get()?.takeIf { isDolby(it.backend) }
                if (activeDolby != null) {
                    val activeBackend = coordinator.backend(activeDolby.backend)
                    val restored = if (activeBackend is kr.dwas.dwas_EQ.backend.DolbyEqBackend && activeDolby.baselineEnabled != null) {
                        activeBackend.restoreAndVerify(activeDolby.baseline, activeDolby.baselineEnabled)
                    } else {
                        activeBackend.restoreAndVerify(activeDolby.baseline)
                    }
                    if (!restored.ok) {
                        attempts += "Android Equalizer fallback blocked because ${activeDolby.backend.label} baseline restore failed: ${restored.message}"
                        continue
                    }
                    BackendTransactionRuntime.clear()
                }
            }
            val gate = routeGateReason(backend.kind, route)
            val outcome = BackendApplyEngine.apply(listOf(backend), target) { gate }
            if (outcome.result.ok && outcome.result.value != null && outcome.baseline != null) {
                PersistentDolbyRuntime.release()
                val stableBaseline = if (isDolby(outcome.result.value)) preservedBaseline ?: outcome.baseline else outcome.baseline
                val stableBaselineEnabled = if (isDolby(outcome.result.value)) preservedBaselineEnabled ?: outcome.baselineEnabled else null
                BackendTransactionRuntime.set(outcome.result.value, stableBaseline, stableBaselineEnabled)
                return BackendResult(true, outcome.result.value, attempts.plus(outcome.result.message).joinToString(" | "))
            }
            attempts += outcome.result.message
        }
        return BackendResult(false, message = "No backend applied the EQ. ${attempts.joinToString(" | ")}")
    }

    fun verifyAppliedCurve(curve: EqCurve): BackendResult<BackendKind> {
        val target = NineBandEqAdapter.uiDbToDap(curve.toFloatArray())
        val persistent = PersistentDolbyRuntime.activeTransaction()
        if (persistent != null) {
            val gains = PersistentDolbyRuntime.readHeld()
            val enabled = PersistentDolbyRuntime.readHeldEnabled()
            val ok = gains?.contentEquals(target) == true && enabled == true
            return if (ok) BackendResult(true, BackendKind.DIRECT_DOLBY, "Persistent Direct Dolby post-FX GEQ readback verified.")
            else BackendResult(false, message = "Persistent Direct Dolby post-FX GEQ readback differs from the applied target.")
        }
        val transaction = BackendTransactionRuntime.get()
        if (transaction?.backend == BackendKind.ANDROID_EQUALIZER || (transaction == null && settings.androidEqArmed)) {
            val verified = AndroidEqualizerRuntime.verifyApplied(appContext, target)
            return if (verified.ok) BackendResult(true, BackendKind.ANDROID_EQUALIZER, verified.message)
            else BackendResult(false, message = verified.message)
        }
        if (transaction != null) {
            val backend = coordinator.backend(transaction.backend)
            val gains = backend.readGains()
            if (!gains.ok || gains.value?.contentEquals(target) != true) {
                return BackendResult(false, message = "${transaction.backend.label} post-FX gain readback differs from the applied target: ${gains.message}")
            }
            if (backend is kr.dwas.dwas_EQ.backend.DolbyEqBackend) {
                val enabled = backend.readGeqEnabled()
                if (!enabled.ok || enabled.value != true) {
                    return BackendResult(false, message = "${transaction.backend.label} post-FX GEQ enabled readback failed: ${enabled.message}")
                }
            }
            return BackendResult(true, transaction.backend, "${transaction.backend.label} post-FX EQ readback verified.")
        }
        return BackendResult(false, message = "No active EQ transaction is available for post-FX verification.")
    }

    fun restore(route: RouteState): BackendResult<BackendKind> {
        val persistent = PersistentDolbyRuntime.activeTransaction()
        if (persistent != null) {
            routeGateReason(BackendKind.DIRECT_DOLBY, route)?.let { reason ->
                return BackendResult(false, message = reason.replace("trying the next safe backend.", "restore was not attempted."))
            }
            if (PersistentDolbyRuntime.restoreAndRelease()) {
                BackendTransactionRuntime.clear()
                return BackendResult(true, BackendKind.DIRECT_DOLBY, "Original gains restored through the persistent Direct Dolby transaction.")
            }
            return BackendResult(false, message = "Persistent Direct Dolby baseline restore could not be verified; session was released.")
        }

        val transaction = BackendTransactionRuntime.get()
        if (transaction == null && settings.androidEqArmed) {
            val restored = AndroidEqualizerRuntime.restoreAndRelease()
            return if (restored.ok) BackendResult(true, BackendKind.ANDROID_EQUALIZER, restored.message)
            else BackendResult(false, message = restored.message)
        }
        if (transaction == null) return BackendResult(false, message = "No active EQ baseline is available in this app process.")
        routeGateReason(transaction.backend, route)?.let { reason ->
            return BackendResult(false, message = reason.replace("trying the next safe backend.", "restore was not attempted."))
        }
        val backend = coordinator.backend(transaction.backend)
        val probe = backend.probe()
        if (!probe.available || !probe.hasControl) {
            return BackendResult(false, message = "${transaction.backend.label} is no longer controllable: ${probe.details}")
        }
        val restored = if (backend is kr.dwas.dwas_EQ.backend.DolbyEqBackend && transaction.baselineEnabled != null) {
            backend.restoreAndVerify(transaction.baseline, transaction.baselineEnabled)
        } else {
            backend.restoreAndVerify(transaction.baseline)
        }
        if (restored.ok) BackendTransactionRuntime.clear()
        return if (restored.ok) BackendResult(true, transaction.backend, "Original gains restored and verified through ${transaction.backend.label}.")
        else BackendResult(false, message = "Restore through ${transaction.backend.label} failed: ${restored.message}")
    }

    fun resetControlAccess() {
        runCatching { WiredAdbSocketClient(appContext).request("control_reset") }
        PersistentDolbyRuntime.release()
        BackendTransactionRuntime.clear()
        coordinator.releaseControlAccess()
        WiredAdbBridgeAuth.clear(appContext)
        WiredAdbBridgeRegistry.markDisconnected("Control access and wired ADB artifacts were reset in dwas_EQ.")
    }

    fun safeProbe(route: RouteState): BackendResult<BackendKind> {
        if (settings.controlAccessReset) return BackendResult(false, message = appContext.getString(R.string.diagnostics_control_access_reset_detail))
        val attempts = mutableListOf<String>()
        for (strategy in BackendPolicy.strategyOrder(settings.backendPreference, settings.rootOptIn)) {
            if (strategy == BackendStrategy.ANDROID_EQUALIZER) continue
            if (strategy == BackendStrategy.PERSISTENT_DIRECT) {
                if (!persistentDirectAllowed(route)) {
                    attempts += "Persistent Direct Dolby: route blocked"
                    continue
                }
                val held = PersistentDolbyRuntime.safeProbeHeld()
                if (held != null) {
                    if (held.writeVerified && held.restoreVerified) {
                        return BackendResult(true, BackendKind.DIRECT_DOLBY, "Persistent Direct Dolby: Safe Probe write and restore verified.")
                    }
                    attempts += "Persistent Direct Dolby: Safe Probe restore not verified; session released."
                }
                continue
            }
            val backend = coordinator.backend(strategy.backendKind())
            val probe = backend.probe()
            if (!probe.available || !probe.hasControl) {
                attempts += "${backend.kind.label}: ${probe.details}"
                continue
            }
            routeGateReason(backend.kind, route)?.let { attempts += "${backend.kind.label}: $it"; continue }
            val result = coordinator.safeProbe(backend)
            if (result.ok) return BackendResult(true, backend.kind, "${backend.kind.label}: ${result.message}")
            attempts += "${backend.kind.label}: ${result.message}"
        }
        return BackendResult(false, message = "No Dolby Safe Probe succeeded. ${attempts.joinToString(" | ")}")
    }

    fun close() {
        routeMonitor.close()
        coordinator.close()
    }

    private fun BackendStrategy.backendKind(): BackendKind = when (this) {
        BackendStrategy.WIRED_ADB -> BackendKind.WIRED_ADB_DOLBY
        BackendStrategy.TRANSIENT_DIRECT -> BackendKind.DIRECT_DOLBY
        BackendStrategy.ROOT -> BackendKind.ROOT_DOLBY
        BackendStrategy.ANDROID_EQUALIZER -> BackendKind.ANDROID_EQUALIZER
        BackendStrategy.PERSISTENT_DIRECT -> error("Persistent Direct is handled by PersistentDolbyRuntime")
    }
}
