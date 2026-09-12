package kr.dwas.dwas_EQ

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kr.dwas.dwas_EQ.audio.AudioMutationCoordinator
import kr.dwas.dwas_EQ.audio.RouteState
import kr.dwas.dwas_EQ.backend.BackendKind
import kr.dwas.dwas_EQ.backend.BackendPreference
import kr.dwas.dwas_EQ.backend.BackendProbe
import kr.dwas.dwas_EQ.backend.ControlAccessResetGatePolicy
import kr.dwas.dwas_EQ.data.AppPreferences
import kr.dwas.dwas_EQ.data.AppSettings
import kr.dwas.dwas_EQ.device.DeviceSnapshot
import kr.dwas.dwas_EQ.core.NineBandEqAdapter
import kr.dwas.dwas_EQ.domain.BuiltInEqPresetId
import kr.dwas.dwas_EQ.domain.BuiltInEqPresets
import kr.dwas.dwas_EQ.domain.EqCurve
import kr.dwas.dwas_EQ.domain.EqPreset
import kr.dwas.dwas_EQ.persistence.PersistentDolbyRuntime
import kr.dwas.dwas_EQ.persistence.PersistentDolbyService
import kr.dwas.dwas_EQ.standardfx.HeadroomCalculator
import kr.dwas.dwas_EQ.standardfx.StandardFxCapabilities
import kr.dwas.dwas_EQ.standardfx.SoundEffectsMasterPolicy
import kr.dwas.dwas_EQ.standardfx.RuntimeCapabilityRefreshPolicy
import kr.dwas.dwas_EQ.standardfx.StandardFxRuntime
import kr.dwas.dwas_EQ.standardfx.StandardFxSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


data class EqUiState(
    val device: DeviceSnapshot? = null,
    val route: RouteState? = null,
    val settings: AppSettings = AppSettings(),
    val curve: EqCurve = EqCurve.flat(),
    val probes: List<BackendProbe> = emptyList(),
    val activeBackend: BackendKind? = null,
    val status: String = "",
    val technicalDetail: String = "",
    val busy: Boolean = false,
    val wiredAdbStatus: String = "Not connected",
    val safeProbeAvailable: Boolean = false,
    val standardFxCapabilities: StandardFxCapabilities = StandardFxCapabilities(),
    val standardFxStatus: String = "",
    val eqTransactionActive: Boolean = false,
    val preferencesReady: Boolean = false,
)

class EqViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val preferences = AppPreferences(application)
    private val repository = EqualizerRepository(application)
    private val operationMutex = Mutex()

    private suspend fun <T> serializedOperation(block: suspend () -> T): T =
        AudioMutationCoordinator.serialized { operationMutex.withLock { block() } }

    private val _uiState = MutableStateFlow(
        EqUiState(
            device = repository.device,
            route = repository.routeMonitor.state.value,
            wiredAdbStatus = repository.wiredAdbStatusText(),
            eqTransactionActive = repository.hasActiveEqTransaction(),
            status = text(R.string.status_probe_pending),
        )
    )
    val uiState: StateFlow<EqUiState> = _uiState.asStateFlow()
    private var loadedStoredCurve = false
    private var observedInitialBridgeStatus = false
    private var standardFxCommitJob: Job? = null
    @Volatile private var standardFxDraftActive = false

    init {
        viewModelScope.launch {
            preferences.settings.collectLatest { settings ->
                repository.updateSettings(settings)
                if (!loadedStoredCurve) {
                    loadedStoredCurve = true
                    _uiState.update { it.copy(settings = settings, curve = if (settings.eqEnabled) settings.uiCurve else EqCurve.flat(), preferencesReady = true) }
                    refreshCapabilities()
                    if (settings.controlAccessReset) {
                        viewModelScope.launch(Dispatchers.IO) { restoreControlAccessAfterVerifiedWiredAdb() }
                    }
                } else {
                    _uiState.update { old ->
                        val mergedSettings = if (standardFxDraftActive) settings.copy(standardFx = old.settings.standardFx) else settings
                        old.copy(
                            settings = mergedSettings,
                            preferencesReady = true,
                            safeProbeAvailable = old.route?.let { repository.safeProbeAvailable(it, old.probes) } ?: false,
                            eqTransactionActive = settings.persistentEqArmed || settings.androidEqArmed || repository.hasActiveEqTransaction(),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.routeMonitor.state.collectLatest { route ->
                _uiState.update { old ->
                    old.copy(route = route, safeProbeAvailable = repository.safeProbeAvailable(route, old.probes))
                }
            }
        }
        viewModelScope.launch {
            repository.wiredAdbStatus.collectLatest { status ->
                _uiState.update { it.copy(wiredAdbStatus = status) }
                if (status.startsWith("Connected") && _uiState.value.settings.controlAccessReset) {
                    viewModelScope.launch(Dispatchers.IO) { restoreControlAccessAfterVerifiedWiredAdb() }
                }
                if (observedInitialBridgeStatus && status.startsWith("Connected")) refreshStandardFxCapabilitiesAfterBridgeConnect()
                observedInitialBridgeStatus = true
            }
        }
    }

    private fun text(@StringRes id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun backendName(kind: BackendKind): String = text(
        when (kind) {
            BackendKind.WIRED_ADB_DOLBY -> R.string.backend_wired_adb_dolby
            BackendKind.DIRECT_DOLBY -> R.string.backend_direct_dolby
            BackendKind.ROOT_DOLBY -> R.string.backend_root_dolby
            BackendKind.ANDROID_EQUALIZER -> R.string.backend_android_equalizer
        }
    )

    private fun soundEffectsRuntimeEnabled(settings: AppSettings): Boolean =
        settings.eqEnabled

    private suspend fun cancelPendingStandardFxCommit() {
        standardFxCommitJob?.cancelAndJoin()
        standardFxCommitJob = null
        standardFxDraftActive = false
    }

    private suspend fun restoreControlAccessAfterVerifiedWiredAdb() {
        val current = _uiState.value
        if (!current.settings.controlAccessReset) return
        val ready = repository.verifyWiredAdbReadyAfterReset()
        if (!ControlAccessResetGatePolicy.shouldUnlockAfterVerifiedWiredAdb(current.settings.controlAccessReset, ready)) return
        val reactivateMaster = ControlAccessResetGatePolicy.shouldReactivateMasterAfterVerifiedWiredAdb(current.settings.controlAccessReset, ready)
        preferences.setControlAccessReset(false)
        repository.allowControlAccess()
        _uiState.update {
            it.copy(
                settings = it.settings.copy(controlAccessReset = false),
                wiredAdbStatus = repository.wiredAdbStatusText(),
                status = text(R.string.status_control_access_reenabled),
                technicalDetail = "",
            )
        }
        if (reactivateMaster) setEqEnabled(true)
    }

    private fun verifyActiveEqAfterStandardFx(curve: EqCurve): String? {
        if (!repository.hasActiveEqTransaction()) return null
        val verification = repository.verifyAppliedCurve(curve)
        return verification.message.takeIf { !verification.ok }
    }

    private suspend fun applyStandardFxRuntimeOnly(updated: StandardFxSettings) {
        val snapshot = _uiState.value
        val shouldRun = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), updated)
        val result = if (shouldRun) {
            val headroomCurve = HeadroomCalculator.appliedCurve(snapshot.eqTransactionActive, snapshot.settings.lastAppliedCurve)
            StandardFxRuntime.apply(app, updated, headroomCurve)
        } else {
            StandardFxRuntime.release(app)
            null
        }
        val eqFailure = if (result != null) verifyActiveEqAfterStandardFx(snapshot.settings.lastAppliedCurve) else null
        if (eqFailure != null) StandardFxRuntime.release(app)
        _uiState.update {
            it.copy(
                standardFxStatus = eqFailure ?: result?.message ?: text(R.string.status_fx_disabled),
                status = if (!soundEffectsRuntimeEnabled(snapshot.settings)) {
                    text(R.string.status_fx_saved_master_off)
                } else if (eqFailure != null || (result != null && !result.ok)) {
                    text(R.string.status_fx_partial)
                } else {
                    text(R.string.status_fx_updated)
                },
            )
        }
    }

    private suspend fun persistAndApplyStandardFx(updated: StandardFxSettings) {
        preferences.setStandardFx(updated)
        val snapshot = _uiState.value
        val shouldRun = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), updated)
        val result = if (shouldRun) {
            val headroomCurve = HeadroomCalculator.appliedCurve(snapshot.eqTransactionActive, snapshot.settings.lastAppliedCurve)
            StandardFxRuntime.apply(app, updated, headroomCurve)
        } else {
            StandardFxRuntime.release(app)
            null
        }
        val eqFailure = if (result != null) verifyActiveEqAfterStandardFx(snapshot.settings.lastAppliedCurve) else null
        if (eqFailure != null) StandardFxRuntime.release(app)
        if (shouldRun) PersistentDolbyService.start(app)
        else if (!(snapshot.settings.persistentEqArmed || snapshot.settings.androidEqArmed || repository.hasActiveEqTransaction())) PersistentDolbyService.stop(app)
        if (_uiState.value.settings.standardFx == updated) standardFxDraftActive = false
        _uiState.update {
            it.copy(
                settings = it.settings.copy(standardFx = if (standardFxDraftActive) it.settings.standardFx else updated),
                standardFxStatus = eqFailure ?: result?.message ?: text(R.string.status_fx_disabled),
                status = if (!soundEffectsRuntimeEnabled(snapshot.settings)) {
                    text(R.string.status_fx_saved_master_off)
                } else if (eqFailure != null || (result != null && !result.ok)) {
                    text(R.string.status_fx_partial)
                } else {
                    text(R.string.status_fx_updated)
                },
            )
        }
    }

    fun setGain(index: Int, db: Float) {
        _uiState.update { old -> old.copy(curve = old.curve.withBand(index, db)) }
    }

    fun persistCurve() = viewModelScope.launch { preferences.setUiCurve(_uiState.value.curve) }

    fun resetFlat() {
        _uiState.update { it.copy(curve = EqCurve.flat(), status = text(R.string.status_flat_prepared)) }
        persistCurve()
    }

    private suspend fun updateProbeState(
        statusAfter: String,
        preserveBackendProbes: Boolean = false,
        preserveStandardFxProbe: Boolean = false,
    ) {
        val current = _uiState.value
        val probes = if (preserveBackendProbes) current.probes else repository.probeAll()
        val fxCapabilities = if (preserveStandardFxProbe) {
            current.standardFxCapabilities
        } else {
            StandardFxRuntime.probeCapabilities(app, force = true)
        }
        _uiState.update { old ->
            old.copy(
                probes = probes,
                busy = false,
                wiredAdbStatus = repository.wiredAdbStatusText(),
                safeProbeAvailable = old.route?.let { repository.safeProbeAvailable(it, probes) } ?: false,
                standardFxCapabilities = fxCapabilities,
                status = statusAfter,
            )
        }
    }

    private fun refreshStandardFxCapabilitiesAfterBridgeConnect() = viewModelScope.launch(Dispatchers.IO) {
        val snapshot = _uiState.value
        val runtimeActive = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), snapshot.settings.standardFx)
        if (RuntimeCapabilityRefreshPolicy.shouldPreserveStandardFxCapabilities(runtimeActive, snapshot.standardFxCapabilities)) return@launch
        val capabilities = StandardFxRuntime.probeCapabilities(app, force = true)
        _uiState.update {
            it.copy(
                standardFxCapabilities = capabilities,
                wiredAdbStatus = repository.wiredAdbStatusText(),
            )
        }
    }

    fun refreshCapabilities() = viewModelScope.launch(Dispatchers.IO) {
        repository.refreshWiredAdbStatus()
        serializedOperation {
            val snapshot = _uiState.value
            val preserveBackendProbes = RuntimeCapabilityRefreshPolicy.shouldPreserveBackendProbes(
                activeRuntimeTransaction = repository.hasActiveEqTransaction(),
                persistentEqArmed = snapshot.settings.persistentEqArmed,
                androidEqArmed = snapshot.settings.androidEqArmed,
                controlAccessReset = snapshot.settings.controlAccessReset,
            )
            val runtimeActive = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), snapshot.settings.standardFx)
            val preserveStandardFxProbe = RuntimeCapabilityRefreshPolicy.shouldPreserveStandardFxCapabilities(
                runtimeActive = runtimeActive,
                capabilities = snapshot.standardFxCapabilities,
            )
            repository.currentRoute()
            _uiState.update { it.copy(status = text(R.string.status_running_probe)) }
            updateProbeState(
                text(if (preserveBackendProbes || preserveStandardFxProbe) R.string.status_probe_preserved_active else R.string.status_probe_complete),
                preserveBackendProbes = preserveBackendProbes,
                preserveStandardFxProbe = preserveStandardFxProbe,
            )
        }
    }

    fun apply() = viewModelScope.launch(Dispatchers.IO) {
        cancelPendingStandardFxCommit()
        serializedOperation {
            val snapshot = _uiState.value
            if (snapshot.settings.controlAccessReset) {
                _uiState.update {
                    it.copy(
                        status = text(R.string.status_control_access_required),
                        technicalDetail = text(R.string.diagnostics_control_access_reset_detail),
                    )
                }
                return@serializedOperation
            }
            preferences.setStandardFx(snapshot.settings.standardFx)
            val route = repository.currentRoute()

            if (!snapshot.settings.eqEnabled) {
                _uiState.update { it.copy(busy = true, status = text(R.string.status_eq_disabling)) }
                val hadActiveTransaction = repository.hasActiveEqTransaction()
                val restoreResult = if (hadActiveTransaction) repository.restore(route) else null
                if (restoreResult != null && !restoreResult.ok) {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            status = text(R.string.status_eq_disable_failed),
                            technicalDetail = restoreResult.message,
                        )
                    }
                    return@serializedOperation
                }

                preferences.disarmPersistentEq()
                preferences.disarmAndroidEqualizer()
                val fx = snapshot.settings.standardFx
                if (SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), fx)) {
                    val fxResult = StandardFxRuntime.apply(app, fx, EqCurve.flat())
                    PersistentDolbyService.start(app)
                    _uiState.update { it.copy(standardFxStatus = fxResult.message) }
                } else {
                    PersistentDolbyService.stop(app)
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        activeBackend = null,
                        status = text(R.string.status_eq_disabled),
                        technicalDetail = "",
                        eqTransactionActive = repository.hasActiveEqTransaction(),
                    )
                }
                return@serializedOperation
            }

            _uiState.update { it.copy(busy = true, status = text(R.string.status_reacquiring_dolby)) }
            val result = repository.apply(snapshot.curve, route)
            var postFxVerificationFailure: String? = null
            if (result.ok) {
                preferences.setUiCurve(snapshot.curve)
                if (result.value == BackendKind.ANDROID_EQUALIZER) {
                    preferences.armAndroidEqualizer(snapshot.curve)
                    preferences.disarmPersistentEq()
                    PersistentDolbyService.start(app)
                } else if (result.value != null) {
                    preferences.disarmAndroidEqualizer()
                    preferences.armLastApplied(snapshot.curve)
                    PersistentDolbyService.start(app)
                }
                if (SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), snapshot.settings.standardFx)) {
                    val fx = StandardFxRuntime.apply(app, snapshot.settings.standardFx, snapshot.curve)
                    val eqFailure = verifyActiveEqAfterStandardFx(snapshot.curve)
                    if (eqFailure != null) {
                        StandardFxRuntime.release(app)
                        postFxVerificationFailure = eqFailure
                    }
                    _uiState.update { it.copy(standardFxStatus = eqFailure ?: fx.message) }
                }
            }
            val status = if (result.ok && result.value != null && postFxVerificationFailure == null) {
                text(R.string.status_apply_success, backendName(result.value))
            } else {
                text(R.string.status_apply_failed)
            }
            _uiState.update {
                it.copy(
                    busy = false,
                    activeBackend = result.value,
                    status = status,
                    technicalDetail = if (!result.ok) result.message else postFxVerificationFailure.orEmpty(),
                    eqTransactionActive = repository.hasActiveEqTransaction(),
                )
            }
        }
    }

    fun restore() = viewModelScope.launch(Dispatchers.IO) {
        serializedOperation {
            val snapshot = _uiState.value
            val route = repository.currentRoute()
            val baseline = repository.activeBaseline()
            _uiState.update { it.copy(busy = true, status = text(R.string.status_restoring)) }
            val result = repository.restore(route)
            val restoredCurve = baseline?.let { EqCurve.of(NineBandEqAdapter.dapToUiDb(it)) }
            if (result.ok) {
                preferences.disarmPersistentEq()
                preferences.disarmAndroidEqualizer()
                if (restoredCurve != null) preferences.setUiCurve(restoredCurve)
                if (SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), snapshot.settings.standardFx)) PersistentDolbyService.start(app)
                else PersistentDolbyService.stop(app)
            }
            val status = if (result.ok && result.value != null) {
                text(R.string.status_restore_success, backendName(result.value))
            } else {
                text(R.string.status_restore_failed)
            }
            _uiState.update {
                it.copy(
                    busy = false,
                    activeBackend = result.value,
                    status = status,
                    technicalDetail = if (result.ok) "" else result.message,
                    eqTransactionActive = repository.hasActiveEqTransaction(),
                    curve = if (result.ok) restoredCurve ?: it.curve else it.curve,
                )
            }
        }
    }

    fun runSafeProbe() = viewModelScope.launch(Dispatchers.IO) {
        serializedOperation {
            val snapshot = _uiState.value
            val route = repository.currentRoute()
            if (!snapshot.safeProbeAvailable) {
                _uiState.update { it.copy(status = text(R.string.status_no_safe_backend)) }
                return@serializedOperation
            }
            _uiState.update { it.copy(busy = true, status = text(R.string.status_running_safe_probe)) }
            val result = repository.safeProbe(route)
            val status = if (result.ok && result.value != null) {
                text(R.string.status_safe_probe_success, backendName(result.value))
            } else {
                text(R.string.status_safe_probe_failed)
            }
            updateProbeState(
                status,
                preserveBackendProbes = repository.hasActiveEqTransaction(),
                preserveStandardFxProbe = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(snapshot.settings), snapshot.settings.standardFx),
            )
            _uiState.update {
                it.copy(
                    activeBackend = result.value,
                    technicalDetail = if (result.ok) "" else result.message,
                )
            }
        }
    }

    fun setBackend(value: BackendPreference) = viewModelScope.launch { preferences.setBackend(value) }
    fun setRootOptIn(value: Boolean) = viewModelScope.launch { preferences.setRootOptIn(value) }
    fun setNonSpeakerOverride(value: Boolean) = viewModelScope.launch { preferences.setNonSpeakerOverride(value) }

    fun setEqEnabled(value: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        cancelPendingStandardFxCommit()
        serializedOperation {
            val snapshot = _uiState.value
            if (snapshot.settings.controlAccessReset) {
                _uiState.update {
                    it.copy(
                        status = text(R.string.status_control_access_required),
                        technicalDetail = text(R.string.diagnostics_control_access_reset_detail),
                    )
                }
                return@serializedOperation
            }
            preferences.setStandardFx(snapshot.settings.standardFx)
            val route = repository.currentRoute()

            if (value) {
                val preservedCurve = snapshot.settings.uiCurve
                _uiState.update {
                    it.copy(
                        busy = true,
                        settings = it.settings.copy(eqEnabled = true),
                        status = text(R.string.status_reacquiring_dolby),
                    )
                }
                preferences.setEqEnabled(true)
                val result = repository.apply(preservedCurve, route)
                if (!result.ok) {
                    if (repository.shouldDeferFailedEqEnableUntilPlayback()) {
                        preferences.setUiCurve(preservedCurve)
                        preferences.disarmPersistentEq()
                        preferences.armAndroidEqualizer(preservedCurve)
                        StandardFxRuntime.release(app)
                        PersistentDolbyService.start(app)
                        _uiState.update {
                            it.copy(
                                busy = false,
                                settings = it.settings.copy(eqEnabled = true, uiCurve = preservedCurve),
                                activeBackend = null,
                                status = text(R.string.status_eq_reenabled),
                                technicalDetail = "EQ is enabled and waiting for an active media session. ${result.message}",
                                eqTransactionActive = true,
                                curve = preservedCurve,
                            )
                        }
                        return@serializedOperation
                    }
                    preferences.setEqEnabled(false)
                    _uiState.update {
                        it.copy(
                            busy = false,
                            settings = it.settings.copy(eqEnabled = false),
                            status = text(R.string.status_apply_failed),
                            technicalDetail = result.message,
                            eqTransactionActive = repository.hasActiveEqTransaction(),
                        )
                    }
                    return@serializedOperation
                }

                preferences.setUiCurve(preservedCurve)
                if (result.value == BackendKind.ANDROID_EQUALIZER) {
                    preferences.armAndroidEqualizer(preservedCurve)
                    preferences.disarmPersistentEq()
                } else if (result.value != null) {
                    preferences.disarmAndroidEqualizer()
                    preferences.armLastApplied(preservedCurve)
                }
                val enabledSettings = snapshot.settings.copy(eqEnabled = true, uiCurve = preservedCurve)
                val fxActive = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(enabledSettings), enabledSettings.standardFx)
                val fxResult = if (fxActive) {
                    StandardFxRuntime.apply(app, enabledSettings.standardFx, preservedCurve)
                } else {
                    StandardFxRuntime.release(app)
                    null
                }
                val eqFailure = if (fxResult != null) verifyActiveEqAfterStandardFx(preservedCurve) else null
                if (eqFailure != null) StandardFxRuntime.release(app)
                if (result.value != null || (fxActive && eqFailure == null)) PersistentDolbyService.start(app) else PersistentDolbyService.stop(app)
                _uiState.update {
                    it.copy(
                        busy = false,
                        settings = it.settings.copy(eqEnabled = true, uiCurve = preservedCurve),
                        activeBackend = result.value,
                        standardFxStatus = eqFailure ?: fxResult?.message ?: if (fxActive) it.standardFxStatus else text(R.string.status_fx_disabled),
                        status = if (eqFailure == null) text(R.string.status_eq_reenabled) else text(R.string.status_apply_failed),
                        technicalDetail = eqFailure.orEmpty(),
                        eqTransactionActive = repository.hasActiveEqTransaction(),
                        curve = preservedCurve,
                    )
                }
                return@serializedOperation
            }

            _uiState.update {
                it.copy(
                    busy = true,
                    settings = it.settings.copy(eqEnabled = false, uiCurve = snapshot.curve),
                    status = text(R.string.status_eq_disabling),
                )
            }

            preferences.setEqEnabled(false)
            StandardFxRuntime.release(app)
            val restoreResult = if (repository.hasActiveEqTransaction()) repository.restore(route) else null
            if (restoreResult != null && !restoreResult.ok) {
                preferences.setEqEnabled(true)
                val restoredSettings = snapshot.settings.copy(eqEnabled = true, uiCurve = snapshot.curve)
                val fxActive = SoundEffectsMasterPolicy.shouldRun(soundEffectsRuntimeEnabled(restoredSettings), restoredSettings.standardFx)
                val fxResult = if (fxActive) {
                    val headroomCurve = HeadroomCalculator.appliedCurve(snapshot.eqTransactionActive, snapshot.settings.lastAppliedCurve)
                    StandardFxRuntime.apply(app, restoredSettings.standardFx, headroomCurve)
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        settings = restoredSettings,
                        status = text(R.string.status_eq_disable_failed),
                        technicalDetail = restoreResult.message,
                        standardFxStatus = fxResult?.message ?: it.standardFxStatus,
                    )
                }
                return@serializedOperation
            }

            preferences.setUiCurve(snapshot.curve)
            preferences.disarmPersistentEq()
            preferences.disarmAndroidEqualizer()
            PersistentDolbyService.stop(app)

            _uiState.update {
                it.copy(
                    busy = false,
                    settings = it.settings.copy(eqEnabled = false, uiCurve = snapshot.curve),
                    activeBackend = null,
                    status = text(R.string.status_eq_disabled),
                    technicalDetail = "",
                    standardFxStatus = text(R.string.status_fx_disabled),
                    eqTransactionActive = repository.hasActiveEqTransaction(),
                    curve = EqCurve.flat(),
                )
            }
        }
    }

    fun setDiagnosticsEnabled(value: Boolean) = viewModelScope.launch { preferences.setDiagnosticsEnabled(value) }

    fun clearTransientStatus() {
        _uiState.update { it.copy(status = "") }
    }

    fun acceptHearingSafety() = viewModelScope.launch {
        preferences.setHearingSafetyAcceptedVersion(kr.dwas.dwas_EQ.safety.HearingSafetyPolicy.CURRENT_VERSION)
    }

    fun acceptExperimentalAppWarning() = viewModelScope.launch {
        preferences.setExperimentalWarningAcknowledged(true)
    }

    fun resetAllControlAccess() = viewModelScope.launch(Dispatchers.IO) {
        cancelPendingStandardFxCommit()
        serializedOperation {
            val snapshot = _uiState.value
            val route = repository.currentRoute()
            _uiState.update { it.copy(busy = true, status = text(R.string.status_resetting_control_access)) }
            val restore = if (repository.hasActiveEqTransaction()) repository.restore(route) else null
            if (restore != null && !restore.ok) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = text(R.string.status_reset_control_access_failed),
                        technicalDetail = restore.message,
                    )
                }
                return@serializedOperation
            }
            preferences.setUiCurve(snapshot.curve)
            preferences.setEqEnabled(false)
            preferences.disarmPersistentEq()
            preferences.disarmAndroidEqualizer()
            preferences.setRootOptIn(false)
            preferences.setNonSpeakerOverride(false)
            StandardFxRuntime.release(app)
            PersistentDolbyService.stop(app)
            repository.resetControlAccess()
            preferences.setControlAccessReset(true)
            val resetSettings = snapshot.settings.copy(
                rootOptIn = false,
                nonSpeakerOverride = false,
                uiCurve = snapshot.curve,
                eqEnabled = false,
                persistentEqArmed = false,
                androidEqArmed = false,
                soundEffectsEnabled = false,
                controlAccessReset = true,
            )
            repository.updateSettings(resetSettings)
            val resetProbes = repository.probeAll()
            _uiState.update {
                it.copy(
                    busy = false,
                    settings = resetSettings,
                    curve = snapshot.curve,
                    probes = resetProbes,
                    activeBackend = null,
                    wiredAdbStatus = repository.wiredAdbStatusText(),
                    safeProbeAvailable = false,
                    standardFxStatus = text(R.string.status_fx_disabled),
                    status = text(R.string.status_reset_control_access_complete),
                    technicalDetail = "",
                    eqTransactionActive = false,
                )
            }
        }
    }

    fun resetChannelBalance() = setStandardFx {
        it.copy(leftBalanceDb = 0f, rightBalanceDb = 0f)
    }

    fun resetLimiter() = setStandardFx {
        it.copy(limiter = kr.dwas.dwas_EQ.standardfx.LimiterSettings(enabled = it.limiter.enabled))
    }

    fun resetAudio() = viewModelScope.launch(Dispatchers.IO) {
        cancelPendingStandardFxCommit()
        serializedOperation {
            val snapshot = _uiState.value
            val route = repository.currentRoute()
            _uiState.update { it.copy(busy = true, status = text(R.string.status_resetting_audio)) }
            val restore = if (repository.hasActiveEqTransaction()) repository.restore(route) else null
            if (restore != null && !restore.ok) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        status = text(R.string.status_reset_failed),
                        technicalDetail = restore.message,
                    )
                }
                return@serializedOperation
            }
            val flat = EqCurve.flat()
            val defaultFx = StandardFxSettings()
            preferences.setStandardFx(defaultFx)
            preferences.disarmPersistentEq()
            preferences.disarmAndroidEqualizer()
            preferences.setUiCurve(flat)
            preferences.recordLastAppliedCurve(flat)
            StandardFxRuntime.release(app)
            PersistentDolbyService.stop(app)
            _uiState.update {
                it.copy(
                    busy = false,
                    curve = flat,
                    settings = snapshot.settings.copy(
                        uiCurve = flat,
                        lastAppliedCurve = flat,
                        standardFx = defaultFx,
                        persistentEqArmed = false,
                        androidEqArmed = false,
                    ),
                    activeBackend = null,
                    standardFxStatus = text(R.string.status_fx_disabled),
                    status = text(R.string.status_reset_complete),
                    technicalDetail = "",
                    eqTransactionActive = false,
                )
            }
        }
    }

    fun stageStandardFx(transform: (StandardFxSettings) -> StandardFxSettings) {
        standardFxDraftActive = true
        _uiState.update { old ->
            val updated = transform(old.settings.standardFx).normalized()
            old.copy(settings = old.settings.copy(standardFx = updated))
        }
        scheduleRealtimeStandardFxApply()
    }

    private fun scheduleRealtimeStandardFxApply() {
        val updated = _uiState.value.settings.standardFx.normalized()
        standardFxCommitJob?.cancel()
        standardFxCommitJob = viewModelScope.launch(Dispatchers.IO) {
            delay(STANDARD_FX_REALTIME_DEBOUNCE_MS)
            serializedOperation {
                applyStandardFxRuntimeOnly(updated)
            }
        }
    }

    fun commitStandardFx() {
        val updated = _uiState.value.settings.standardFx.normalized()
        standardFxCommitJob?.cancel()
        standardFxCommitJob = viewModelScope.launch(Dispatchers.IO) {
            serializedOperation {
                persistAndApplyStandardFx(updated)
            }
        }
    }

    fun setStandardFx(transform: (StandardFxSettings) -> StandardFxSettings) {
        stageStandardFx(transform)
        commitStandardFx()
    }

    fun savePreset(name: String) = viewModelScope.launch {
        val clean = name.trim()
        if (clean.isEmpty()) return@launch
        val snapshot = _uiState.value
        val updated = snapshot.settings.userPresets.toMutableList()
        val index = updated.indexOfFirst { it.name.equals(clean, ignoreCase = true) }
        val preset = EqPreset(clean, snapshot.curve)
        if (index >= 0) updated[index] = preset else updated += preset
        preferences.setUserPresets(updated)
        _uiState.update { it.copy(status = text(R.string.status_preset_saved, clean)) }
    }

    fun renamePreset(index: Int, name: String) = viewModelScope.launch {
        val clean = name.trim()
        val presets = _uiState.value.settings.userPresets
        if (index !in presets.indices || clean.isEmpty()) return@launch
        val updated = presets.toMutableList()
        updated[index] = updated[index].copy(name = clean)
        preferences.setUserPresets(updated)
        _uiState.update { it.copy(status = text(R.string.status_preset_renamed, clean)) }
    }

    fun deletePreset(index: Int) = viewModelScope.launch {
        val presets = _uiState.value.settings.userPresets
        if (index !in presets.indices) return@launch
        val name = presets[index].name
        preferences.setUserPresets(presets.toMutableList().also { it.removeAt(index) })
        _uiState.update { it.copy(status = text(R.string.status_preset_deleted, name)) }
    }

    fun loadPreset(index: Int) {
        val preset = _uiState.value.settings.userPresets.getOrNull(index) ?: return
        _uiState.update { it.copy(curve = preset.curve, status = text(R.string.status_preset_loaded, preset.name)) }
        persistCurve()
    }

    fun loadBuiltInPreset(id: BuiltInEqPresetId) {
        val preset = BuiltInEqPresets.byId(id) ?: return
        _uiState.update { it.copy(curve = preset.curve, status = text(R.string.status_preset_loaded, text(builtInPresetNameRes(preset.id)))) }
        persistCurve()
    }

    fun loadFlatPreset() {
        _uiState.update { it.copy(curve = EqCurve.flat(), status = text(R.string.status_preset_loaded, text(R.string.preset_flat))) }
        persistCurve()
    }

    companion object {
        private const val STANDARD_FX_REALTIME_DEBOUNCE_MS = 40L
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
