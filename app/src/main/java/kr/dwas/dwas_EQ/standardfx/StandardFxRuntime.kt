package kr.dwas.dwas_EQ.standardfx

import android.content.Context
import kr.dwas.dwas_EQ.backend.AndroidEqualizerRuntime
import kr.dwas.dwas_EQ.domain.EqCurve

object StandardFxRuntime {
    private val controller = StandardFxController()
    private val sessionControllers = linkedMapOf<Int, SessionStandardFxController>()
    @Volatile private var lastResult: StandardFxApplyResult? = null
    @Volatile private var lastAppliedSettings: StandardFxSettings? = null
    @Volatile private var lastAppliedCurve: EqCurve? = null
    @Volatile private var lastCapabilities: StandardFxCapabilities? = null
    @Volatile private var lastFullSessionRouting: Boolean? = null
    @Volatile private var lastDirectSessionStrengthRouting: Boolean? = null
    @Volatile private var lastProfile: AudioCompatibilityProfile? = null
    @Volatile private var lastBassOverlayRouting: Boolean = false
    @Volatile private var lastUnifiedEqCoreRouting: Boolean = false
    @Volatile private var lastSessionDetail: String = "Session discovery not checked yet"
    @Volatile private var sessionFxBridgeClient: WiredAdbSessionFxClient? = null

    private data class SessionApplyOutcome(
        val activeEffects: Set<String>,
        val failures: Map<String, String>,
        val bassSucceeded: Boolean,
        val virtualizerSucceeded: Boolean,
        val detail: String,
    )

    @Synchronized
    fun probeCapabilities(context: Context, force: Boolean = false): StandardFxCapabilities {
        if (!force) lastCapabilities?.let { return it }
        val compatibility = StandardFxCompatibilityDetector.snapshot()
        val routingDetail = AudioSessionDiscovery(context.applicationContext).statusText()
        val result = PassiveAudioCapabilityDetector.capabilities(
            profile = compatibility.profile,
            routingDetail = routingDetail,
        )
        lastCapabilities = result
        lastSessionDetail = routingDetail
        return result
    }

    @Synchronized
    fun apply(
        context: Context,
        settings: StandardFxSettings,
        lastAppliedCurveInput: EqCurve,
        forceSessionRefresh: Boolean = false,
    ): StandardFxApplyResult {
        val normalized = settings.normalized()
        val compatibility = StandardFxCompatibilityDetector.snapshot()
        val profile = compatibility.profile
        val fullSessionRouting = compatibility.fullSessionRouting
        val directSessionStrengthRouting = compatibility.directSessionStrengthRouting
        val unifiedEqCoreRouting = PeridotUnifiedEqCorePolicy.shouldUse(
            profile = profile,
            settings = normalized,
            androidEqLogicalTargetAvailable = AndroidEqualizerRuntime.hasLogicalTarget(),
        )
        val curveRequiresReapply = SessionFxStabilityPolicy.curveRequiresStandardFxReapply(
            attenuatorEnabled = normalized.attenuatorEnabled,
            automaticAttenuation = normalized.automaticAttenuation,
        )
        if (
            !forceSessionRefresh &&
            lastAppliedSettings == normalized &&
            ((!unifiedEqCoreRouting && !curveRequiresReapply) || lastAppliedCurve == lastAppliedCurveInput) &&
            lastProfile == profile &&
            lastFullSessionRouting == fullSessionRouting &&
            lastDirectSessionStrengthRouting == directSessionStrengthRouting &&
            lastUnifiedEqCoreRouting == unifiedEqCoreRouting &&
            (!unifiedEqCoreRouting || !AndroidEqualizerRuntime.hasAttachedControllers())
        ) {
            lastResult?.let {
                lastAppliedCurve = lastAppliedCurveInput
                return it
            }
        }

        val initialRoutes = StandardFxRoutingPolicy.routeByProfile(
            requested = normalized,
            profile = profile,
            lastAppliedCurve = lastAppliedCurveInput,
            globalFailedEffects = emptySet(),
        )
        val global = if (initialRoutes.global.requiresRuntimeHold) {
            controller.apply(
                settings = initialRoutes.global,
                lastAppliedCurve = lastAppliedCurveInput,
                nativeBass = initialRoutes.globalNativeBass,
                nativeVirtualizer = initialRoutes.globalNativeVirtualizer,
            )
        } else {
            controller.close()
            StandardFxApplyResult(initialRoutes.global, emptySet(), emptyMap())
        }
        val active = global.activeEffects.toMutableSet()
        val failures = global.failedEffects.toMutableMap()

        val routed = StandardFxRoutingPolicy.routeByProfile(
            requested = normalized,
            profile = profile,
            lastAppliedCurve = lastAppliedCurveInput,
            globalFailedEffects = global.failedEffects.keys,
        )
        failures += routed.unavailableFailures
        if (routed.session.bassBoostEnabled && profile.bass.route == AudioEffectRoute.GLOBAL_PUBLIC_BASS) failures.remove("Bass Boost")
        if (routed.session.virtualizerEnabled && profile.virtualizer.route == AudioEffectRoute.GLOBAL_PUBLIC_VIRTUALIZER) failures.remove("Virtualizer")

        if (lastUnifiedEqCoreRouting && !unifiedEqCoreRouting) {
            sessionFxBridgeClient?.release()
            sessionFxBridgeClient = null
            releaseSessionControllers()
            val resumed = AndroidEqualizerRuntime.resumeAfterSharedSessionDynamics(context.applicationContext)
            if (!resumed.ok) failures["Equalizer"] = resumed.message
        }

        val bassOverlayRouting = profile.bass.route == AudioEffectRoute.ANDROID_EQ_BASS_OVERLAY
        var effectiveUnifiedEqCoreRouting = unifiedEqCoreRouting
        if (unifiedEqCoreRouting) {
            val suspended = AndroidEqualizerRuntime.suspendForSharedSessionDynamics(
                context.applicationContext,
                routed.androidEqBassOverlayStrength,
            )
            if (!suspended.ok) {
                effectiveUnifiedEqCoreRouting = false
                failures["DynamicsProcessing"] = suspended.message
            }
        }
        if (bassOverlayRouting && !effectiveUnifiedEqCoreRouting) {
            val overlay = AndroidEqualizerRuntime.applyBassOverlay(context.applicationContext, routed.androidEqBassOverlayStrength)
            if (routed.androidEqBassOverlayStrength > 0) {
                if (overlay.ok) {
                    active += "Bass Boost"
                    failures.remove("Bass Boost")
                } else {
                    failures["Bass Boost"] = overlay.message
                }
            } else if (!overlay.ok) {
                failures["Bass Boost"] = overlay.message
            }
        } else if (!bassOverlayRouting && lastBassOverlayRouting) {
            val cleared = AndroidEqualizerRuntime.clearBassOverlay(context.applicationContext)
            if (!cleared.ok) failures["Bass Boost"] = cleared.message
        }

        val sessionSettings = if (unifiedEqCoreRouting && !effectiveUnifiedEqCoreRouting) {
            routed.session.copy(
                attenuatorEnabled = false,
                channelBalanceEnabled = false,
                limiter = LimiterSettings(),
            )
        } else {
            routed.session
        }
        val needsSessionFx = sessionSettings.requiresRuntimeHold
        val sessionOutcome = if (needsSessionFx) {
            applySessionEffects(
                context = context,
                settings = sessionSettings,
                forceSessionRefresh = forceSessionRefresh,
                fullDynamicsRouting = routed.sessionFullDynamicsRouting,
                forceVirtualizerMode = routed.sessionForceVirtualizerMode,
                environmentalReverbRouting = routed.sessionEnvironmentalReverb,
                allowVendorProxyStrengthEffects = profile.allowVendorProxyStrengthEffects,
                preferDirectSoftwareStrengthEffects = profile.preferDirectSoftwareStrengthEffects,
                unifiedEqCoreRouting = effectiveUnifiedEqCoreRouting,
                unifiedEqCurve = lastAppliedCurveInput,
                unifiedBassStrength = routed.androidEqBassOverlayStrength,
            )
        } else {
            sessionFxBridgeClient?.release()
            sessionFxBridgeClient = null
            releaseSessionControllers()
            val protected = buildList {
                if (initialRoutes.globalNativeBass) add("Bass Boost")
                if (initialRoutes.globalNativeVirtualizer) add("Virtualizer")
            }
            val detail = if (protected.isEmpty()) {
                "No playback-session effect routing required"
            } else {
                protected.joinToString(", ") + " routed through dwas_EQ DynamicsProcessing because vendor playback proxy is unsafe"
            }
            SessionApplyOutcome(emptySet(), emptyMap(), false, false, detail)
        }

        active += sessionOutcome.activeEffects
        failures += sessionOutcome.failures
        lastSessionDetail = sessionOutcome.detail
        if (effectiveUnifiedEqCoreRouting) {
            val coreActive = "Attenuator" in sessionOutcome.activeEffects ||
                "Channel Balance" in sessionOutcome.activeEffects ||
                "Limiter" in sessionOutcome.activeEffects
            if (!coreActive) {
                releaseSessionControllers()
                val resumed = AndroidEqualizerRuntime.resumeAfterSharedSessionDynamics(context.applicationContext)
                if (!resumed.ok) failures["Equalizer"] = resumed.message
                effectiveUnifiedEqCoreRouting = false
            }
        }

        return remember(
            result = StandardFxApplyResult(normalized, active, failures),
            settings = normalized,
            curve = lastAppliedCurveInput,
            profile = profile,
            fullSessionRouting = fullSessionRouting,
            directSessionStrengthRouting = directSessionStrengthRouting,
            unifiedEqCoreRouting = effectiveUnifiedEqCoreRouting,
        )
    }

    private fun applySessionEffects(
        context: Context,
        settings: StandardFxSettings,
        forceSessionRefresh: Boolean,
        fullDynamicsRouting: Boolean,
        forceVirtualizerMode: Boolean,
        environmentalReverbRouting: Boolean,
        allowVendorProxyStrengthEffects: Boolean,
        preferDirectSoftwareStrengthEffects: Boolean,
        unifiedEqCoreRouting: Boolean,
        unifiedEqCurve: EqCurve,
        unifiedBassStrength: Int,
    ): SessionApplyOutcome {
        val bridgeClient = sessionFxBridgeClient ?: WiredAdbSessionFxClient(context.applicationContext).also {
            sessionFxBridgeClient = it
        }
        val shellResult = if (unifiedEqCoreRouting) {
            bridgeClient.release()
            null
        } else {
            bridgeClient.apply(settings, fullDynamicsRouting, forceVirtualizerMode, environmentalReverbRouting, allowVendorProxyStrengthEffects, preferDirectSoftwareStrengthEffects)
        }
        if (shellResult != null && SessionFxBridgePolicy.isTerminalShellResult(shellResult.ok, shellResult.failedEffects.keys)) {
            releaseSessionControllers()
            return SessionApplyOutcome(
                activeEffects = shellResult.activeEffects,
                failures = shellResult.failedEffects,
                bassSucceeded = settings.bassBoostEnabled && shellResult.failedEffects.keys.none { it.startsWith("Bass Boost") },
                virtualizerSucceeded = settings.virtualizerEnabled && shellResult.failedEffects.keys.none { it.startsWith("Virtualizer") },
                detail = shellResult.detail,
            )
        }
        if (shellResult?.ok == true && shellResult.failedEffects.isNotEmpty()) {
            bridgeClient.release()
        }

        val discovery = AudioSessionDiscovery(context.applicationContext).discover(force = forceSessionRefresh || shellResult != null)
        val detail = listOfNotNull(shellResult?.detail, discovery.detail).joinToString(" · ")
        val sessions = discovery.sessions.filter { it > 0 }.toSet()
        releaseStaleSessions(sessions)

        if (sessions.isEmpty()) {
            val reason = if (discovery.sessionAccessAvailable) {
                "No active USAGE_MEDIA audio session; start music and retry"
            } else {
                "Audio-session access unavailable; rerun tools\\dwas_EQ_ADB_Enable.bat after installing this build"
            }
            val failures = linkedMapOf<String, String>()
            if (settings.attenuatorEnabled) failures["Attenuator"] = reason
            if (settings.channelBalanceEnabled) failures["Channel Balance"] = reason
            if (settings.limiter.enabled) failures["Limiter"] = reason
            if (settings.bassBoostEnabled) failures["Bass Boost"] = reason
            if (settings.virtualizerEnabled) failures["Virtualizer"] = reason
            if (settings.reverbEnabled && settings.reverbPreset != ReverbPreset.NONE) failures["Preset Reverb"] = reason
            return SessionApplyOutcome(emptySet(), failures, false, false, detail)
        }

        val active = linkedSetOf<String>()
        val failures = linkedMapOf<String, String>()
        var bassSucceeded = false
        var virtualizerSucceeded = false
        sessions.sorted().forEach { sessionId ->
            val sessionController = sessionControllers.getOrPut(sessionId) { SessionStandardFxController(sessionId) }
            val result = sessionController.apply(
                settings = settings,
                fullDynamicsRouting = fullDynamicsRouting,
                forceVirtualizerMode = forceVirtualizerMode,
                environmentalReverbRouting = environmentalReverbRouting,
                allowVendorProxyStrengthEffects = allowVendorProxyStrengthEffects,
                preferDirectSoftwareStrengthEffects = preferDirectSoftwareStrengthEffects,
                unifiedEqCoreRouting = unifiedEqCoreRouting,
                unifiedEqCurve = unifiedEqCurve,
                unifiedBassStrength = unifiedBassStrength,
            )
            active += result.activeEffects
            result.failedEffects.forEach { (name, reason) -> failures["$name (session $sessionId)"] = reason }
            if (settings.bassBoostEnabled && "Bass Boost" !in result.failedEffects) bassSucceeded = true
            if (settings.virtualizerEnabled && "Virtualizer" !in result.failedEffects) virtualizerSucceeded = true
        }

        return SessionApplyOutcome(active, failures, bassSucceeded, virtualizerSucceeded, detail)
    }

    private fun remember(
        result: StandardFxApplyResult,
        settings: StandardFxSettings,
        curve: EqCurve,
        profile: AudioCompatibilityProfile,
        fullSessionRouting: Boolean,
        directSessionStrengthRouting: Boolean,
        unifiedEqCoreRouting: Boolean,
    ): StandardFxApplyResult {
        lastAppliedSettings = settings
        lastAppliedCurve = curve
        lastProfile = profile
        lastBassOverlayRouting = profile.bass.route == AudioEffectRoute.ANDROID_EQ_BASS_OVERLAY
        lastFullSessionRouting = fullSessionRouting
        lastDirectSessionStrengthRouting = directSessionStrengthRouting
        lastUnifiedEqCoreRouting = unifiedEqCoreRouting
        lastResult = result
        return result
    }

    fun lastResult(): StandardFxApplyResult? = lastResult
    fun sessionDetail(): String = lastSessionDetail

    @Synchronized
    fun release(context: Context? = null) {
        if (lastUnifiedEqCoreRouting && context != null) {
            AndroidEqualizerRuntime.clearBassOverlay(context.applicationContext)
            releaseSessionControllers()
            AndroidEqualizerRuntime.resumeAfterSharedSessionDynamics(context.applicationContext)
        } else if (lastBassOverlayRouting && context != null) {
            AndroidEqualizerRuntime.clearBassOverlay(context.applicationContext)
        }
        controller.close()
        val bridgeClient = context?.applicationContext?.let(::WiredAdbSessionFxClient) ?: sessionFxBridgeClient
        bridgeClient?.release()
        sessionFxBridgeClient = null
        releaseSessionControllers()
        lastAppliedSettings = null
        lastAppliedCurve = null
        lastFullSessionRouting = null
        lastDirectSessionStrengthRouting = null
        lastProfile = null
        lastBassOverlayRouting = false
        lastUnifiedEqCoreRouting = false
        lastCapabilities = null
        lastResult = null
    }

    private fun releaseStaleSessions(activeSessions: Set<Int>) {
        val stale = sessionControllers.keys.filterNot(activeSessions::contains)
        stale.forEach { id -> sessionControllers.remove(id)?.close() }
    }

    private fun releaseSessionControllers() {
        sessionControllers.values.forEach { it.close() }
        sessionControllers.clear()
    }
}
