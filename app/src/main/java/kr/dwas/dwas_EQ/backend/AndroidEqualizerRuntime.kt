package kr.dwas.dwas_EQ.backend

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import kr.dwas.dwas_EQ.core.EqSpec
import kr.dwas.dwas_EQ.core.DeviceGeqMapper
import kr.dwas.dwas_EQ.core.LogFrequencyProjector
import kr.dwas.dwas_EQ.standardfx.AudioSessionDiscovery
import kr.dwas.dwas_EQ.standardfx.SessionFxStabilityPolicy
import kr.dwas.dwas_EQ.standardfx.SpeakerSafeBassOverlayPolicy
import java.util.UUID

object AndroidEqualizerRuntime {
    private data class SessionState(
        val equalizer: Equalizer,
        val baselineLevels: ShortArray,
        val baselineEnabled: Boolean,
    )

    private val sessions = linkedMapOf<Int, SessionState>()
    private var lastAppliedGains: IntArray? = null
    private var bassOverlayStrength: Int = 0
    private var overlayBaseGains: IntArray? = null
    private var sharedSessionDynamicsSuspended: Boolean = false
    private val EQUALIZER_TYPE_UUID = UUID.fromString("0bed4300-ddd6-11db-8f34-0002a5d5c51b")

    @Synchronized
    fun hasLogicalTarget(): Boolean = lastAppliedGains != null

    @Synchronized
    fun hasAttachedControllers(): Boolean = sessions.isNotEmpty()

    @Synchronized
    fun isSharedSessionDynamicsSuspended(): Boolean = sharedSessionDynamicsSuspended

    @Synchronized
    fun suspendForSharedSessionDynamics(context: Context, targetBassStrength: Int): BackendResult<Unit> {
        val target = lastAppliedGains?.copyOf()
            ?: return BackendResult(false, message = "Android Equalizer has no logical EQ target to hand off to dwas_EQ shared-session DynamicsProcessing.")
        bassOverlayStrength = targetBassStrength.coerceIn(0, 1000)
        overlayBaseGains = null
        if (sharedSessionDynamicsSuspended && sessions.isEmpty()) {
            return BackendResult(true, Unit, "Android Equalizer logical target is already handed off to dwas_EQ shared-session DynamicsProcessing.")
        }
        val failures = mutableListOf<String>()
        sessions.toMap().forEach { (sessionId, state) ->
            runCatching {
                state.baselineLevels.indices.forEach { band ->
                    state.equalizer.setBandLevel(band.toShort(), state.baselineLevels[band])
                }
                val status = state.equalizer.setEnabled(state.baselineEnabled)
                check(status == 0) { "setEnabled restore failed: $status" }
                check(nativeLevelsMatch(state.equalizer, state.baselineLevels)) { "baseline native band readback mismatch" }
                check(runCatching { state.equalizer.enabled }.getOrDefault(false) == state.baselineEnabled) { "baseline enabled readback mismatch" }
            }.onFailure {
                failures += "$sessionId: ${it::class.simpleName}: ${it.message ?: "restore failed"}"
            }
            runCatching { state.equalizer.release() }
        }
        sessions.clear()
        if (failures.isNotEmpty()) {
            sharedSessionDynamicsSuspended = false
            val reapplied = applyAndVerifyInternal(context, target)
            val recovery = if (reapplied.ok) "Android Equalizer target was re-applied." else "Android Equalizer target recovery failed: ${reapplied.message}"
            return BackendResult(false, message = "Android Equalizer handoff baseline restore was not fully verified: ${failures.joinToString(" | ")}. $recovery")
        }
        sharedSessionDynamicsSuspended = true
        return BackendResult(true, Unit, "Android Equalizer native session baseline restored and controller released for dwas_EQ shared-session DynamicsProcessing.")
    }

    @Synchronized
    fun resumeAfterSharedSessionDynamics(context: Context): BackendResult<Unit> {
        if (!sharedSessionDynamicsSuspended) return BackendResult(true, Unit, "Android Equalizer shared-session handoff is already inactive.")
        val target = lastAppliedGains?.copyOf()
        sharedSessionDynamicsSuspended = false
        if (target == null) return BackendResult(true, Unit, "Android Equalizer has no logical target to resume.")
        return applyAndVerify(context, target)
    }

    @Synchronized
    fun probe(context: Context): BackendProbe {
        if (sessions.isNotEmpty()) {
            val controlled = sessions.values.count { runCatching { it.equalizer.hasControl() }.getOrDefault(false) }
            return BackendProbe(
                BackendKind.ANDROID_EQUALIZER,
                available = controlled > 0,
                hasControl = controlled > 0,
                gainCount = sessions.values.firstOrNull()?.equalizer?.numberOfBands?.toInt(),
                details = "Android Equalizer attached to ${sessions.size} active media session(s); controllable=$controlled.",
            )
        }
        val discovery = AudioSessionDiscovery(context.applicationContext).discover()
        val candidates = discovery.sessions.filter { it > 0 }.sorted()
        if (candidates.isEmpty()) {
            return BackendProbe(
                BackendKind.ANDROID_EQUALIZER,
                available = false,
                hasControl = false,
                details = "Android Equalizer requires an active non-zero USAGE_MEDIA audio session. ${discovery.detail}",
            )
        }
        val descriptorPresent = runCatching {
            AudioEffect.queryEffects()?.any { descriptor ->
                descriptor.type == EQUALIZER_TYPE_UUID || descriptor.name.contains("Equalizer", ignoreCase = true)
            } == true
        }.getOrDefault(false)
        return if (descriptorPresent) {
            BackendProbe(
                BackendKind.ANDROID_EQUALIZER,
                available = true,
                hasControl = true,
                details = "Android Equalizer descriptor and active media session are present; control is verified during Apply. ${discovery.detail}",
            )
        } else {
            BackendProbe(
                BackendKind.ANDROID_EQUALIZER,
                available = false,
                hasControl = false,
                details = "Android Equalizer descriptor was not found. ${discovery.detail}",
            )
        }
    }

    @Synchronized
    fun readGains(context: Context): BackendResult<IntArray> {
        if (sharedSessionDynamicsSuspended) {
            val logical = lastAppliedGains
            if (logical != null) return BackendResult(true, logical.copyOf(), "Read retained Android Equalizer logical curve while dwas_EQ shared-session DynamicsProcessing is active.")
        }
        if (bassOverlayStrength > 0) {
            val logicalBase = lastAppliedGains ?: overlayBaseGains
            if (logicalBase != null) {
                return BackendResult(true, logicalBase.copyOf(), "Read logical Android Equalizer base curve while dwas_EQ bass overlay is active.")
            }
        }
        val state = firstControllableState(context)
            ?: return BackendResult(false, message = "No controllable active media session is available for Android Equalizer.")
        return runCatching {
            val (nativeHz, nativeDb) = sortedNative(state.equalizer)
            val uiAnchors = LogFrequencyProjector.interpolate(
                sourceHz = nativeHz,
                sourceDb = nativeDb,
                targetHz = EqSpec.UI_ANCHOR_FREQUENCIES_HZ,
            )
            BackendResult(
                true,
                DeviceGeqMapper.uiDbToDap(uiAnchors),
                "Read Android Equalizer native bands from an active media session.",
            )
        }.getOrElse { BackendResult(false, message = "${it::class.simpleName}: ${it.message ?: "Android EQ read failed"}") }
    }

    @Synchronized
    fun applyAndVerify(context: Context, gains: IntArray): BackendResult<Unit> {
        require(gains.size == 20) { "Expected 20 DAP gains" }
        if (sharedSessionDynamicsSuspended) {
            lastAppliedGains = gains.copyOf()
            return BackendResult(true, Unit, "Android Equalizer logical target updated for dwas_EQ shared-session DynamicsProcessing.")
        }
        return applyAndVerifyInternal(context, gains)
    }

    private fun applyAndVerifyInternal(context: Context, gains: IntArray): BackendResult<Unit> {
        val discovery = AudioSessionDiscovery(context.applicationContext).discover(force = true)
        val activeSessions = discovery.sessions.filter { it > 0 }.toSet()
        releaseStale(activeSessions)
        if (activeSessions.isEmpty()) {
            return BackendResult(false, message = "No active non-zero USAGE_MEDIA audio session is available. ${discovery.detail}")
        }

        val successes = mutableListOf<Int>()
        val failures = mutableListOf<String>()
        activeSessions.sorted().forEach { sessionId ->
            val state = getOrCreateState(sessionId)
            if (state == null) {
                failures += "$sessionId: controller unavailable"
            } else {
                runCatching {
                    val expected = projectedMilliBels(state.equalizer, gains, bassOverlayStrength)
                    applyLevels(state.equalizer, expected)
                    check(runCatching { state.equalizer.enabled }.getOrDefault(false)) { "enabled readback=false" }
                    val levelsMatch = if (bassOverlayStrength > 0) {
                        bassOverlayLevelsMatchAfterSettling(state.equalizer, expected)
                    } else {
                        equalizerLevelsMatchAfterSettling(state.equalizer, expected)
                    }
                    check(levelsMatch) { "native band readback mismatch" }
                }.onSuccess {
                    successes += sessionId
                }.onFailure {
                    failures += "$sessionId: ${it::class.simpleName}: ${it.message ?: "apply failed"}"
                    sessions.remove(sessionId)?.equalizer?.let { eq -> runCatching { eq.release() } }
                }
            }
        }

        if (successes.isEmpty()) {
            return BackendResult(false, message = "Android Equalizer could not attach to an active media session. ${failures.joinToString(" | ")}")
        }
        lastAppliedGains = gains.copyOf()
        return BackendResult(
            true,
            Unit,
            "Android Equalizer native band readback verified on media session(s) ${successes.joinToString()}" +
                if (failures.isEmpty()) "" else "; skipped ${failures.joinToString(" | ")}",
        )
    }

    @Synchronized
    fun applyBassOverlay(context: Context, strength: Int): BackendResult<Unit> {
        sharedSessionDynamicsSuspended = false
        val targetStrength = strength.coerceIn(0, 1000)
        if (targetStrength == 0) return clearBassOverlay(context)
        val discovery = AudioSessionDiscovery(context.applicationContext).discover(force = true)
        val activeSessions = discovery.sessions.filter { it > 0 }.toSet()
        releaseStale(activeSessions)
        if (activeSessions.isEmpty()) {
            return BackendResult(false, message = "No active non-zero USAGE_MEDIA session is available for dwas_EQ bass overlay. ${discovery.detail}")
        }
        if (lastAppliedGains == null && overlayBaseGains == null) {
            val base = readGains(context)
            if (!base.ok || base.value == null) return BackendResult(false, message = base.message)
            overlayBaseGains = base.value.copyOf()
        }
        val logicalBase = lastAppliedGains ?: overlayBaseGains ?: IntArray(20)
        val successes = mutableListOf<Int>()
        val failures = mutableListOf<String>()
        activeSessions.sorted().forEach { sessionId ->
            val state = getOrCreateState(sessionId)
            if (state == null) {
                failures += "$sessionId: controller unavailable"
            } else {
                runCatching {
                    val expected = if (lastAppliedGains != null) {
                        projectedMilliBels(state.equalizer, logicalBase, targetStrength)
                    } else {
                        overlayOnlyExpectedLevels(state.equalizer, state.baselineLevels, targetStrength)
                    }
                    applyLevels(state.equalizer, expected)
                    check(state.equalizer.hasControl()) { "hasControl=false" }
                    check(runCatching { state.equalizer.enabled }.getOrDefault(false)) { "enabled readback=false" }
                    check(bassOverlayLevelsMatchAfterSettling(state.equalizer, expected)) { "bass overlay native band readback mismatch" }
                }.onSuccess {
                    successes += sessionId
                }.onFailure { error ->
                    failures += "$sessionId: ${error::class.simpleName}: ${error.message ?: "bass overlay failed"}"
                    sessions.remove(sessionId)?.equalizer?.let { eq -> runCatching { eq.release() } }
                }
            }
        }
        if (successes.isEmpty()) {
            return BackendResult(false, message = "dwas_EQ bass overlay could not attach to an active media session. ${failures.joinToString(" | ")}")
        }
        bassOverlayStrength = targetStrength
        return BackendResult(
            true,
            Unit,
            "dwas_EQ bass overlay native band readback verified on media session(s) ${successes.joinToString()}" +
                if (failures.isEmpty()) "" else "; skipped ${failures.joinToString(" | ")}",
        )
    }

    @Synchronized
    fun clearBassOverlay(context: Context): BackendResult<Unit> {
        if (sharedSessionDynamicsSuspended) {
            bassOverlayStrength = 0
            overlayBaseGains = null
            return BackendResult(true, Unit, "dwas_EQ shared-session bass overlay target cleared without reattaching Android Equalizer.")
        }
        if (bassOverlayStrength == 0) return BackendResult(true, Unit, "dwas_EQ bass overlay is already inactive.")
        bassOverlayStrength = 0
        overlayBaseGains = null
        val base = lastAppliedGains?.copyOf()
        return if (base != null) {
            applyAndVerify(context, base)
        } else {
            restoreAndRelease()
        }
    }

    @Synchronized
    fun reapplyArmed(context: Context, gains: IntArray): BackendResult<Unit> {
        require(gains.size == 20) { "Expected 20 DAP gains" }
        if (sharedSessionDynamicsSuspended) {
            lastAppliedGains = gains.copyOf()
            return BackendResult(true, Unit, "Android Equalizer logical target updated while dwas_EQ shared-session DynamicsProcessing remains active.")
        }
        return applyAndVerify(context, gains)
    }

    @Synchronized
    fun verifyApplied(context: Context, gains: IntArray): BackendResult<Unit> {
        require(gains.size == 20) { "Expected 20 DAP gains" }
        if (sharedSessionDynamicsSuspended) {
            return if (lastAppliedGains?.contentEquals(gains) == true) {
                BackendResult(true, Unit, "Android Equalizer logical target retained while dwas_EQ shared-session DynamicsProcessing owns the active EQ stage.")
            } else {
                BackendResult(false, message = "Android Equalizer logical target differs while dwas_EQ shared-session DynamicsProcessing is active.")
            }
        }
        val activeSessions = AudioSessionDiscovery(context.applicationContext).discover().sessions.filter { it > 0 }.toSet()
        releaseStale(activeSessions)
        val retained = sessions.filterKeys(activeSessions::contains)
        if (retained.isEmpty()) return BackendResult(false, message = "No retained Android Equalizer controller is available for readback verification.")
        val failures = mutableListOf<String>()
        retained.forEach { (sessionId, state) ->
            runCatching {
                check(state.equalizer.hasControl()) { "hasControl=false" }
                check(state.equalizer.enabled) { "enabled readback=false" }
                val expected = projectedMilliBels(state.equalizer, gains, bassOverlayStrength)
                val levelsMatch = if (bassOverlayStrength > 0) {
                    bassOverlayLevelsMatchAfterSettling(state.equalizer, expected)
                } else {
                    equalizerLevelsMatchAfterSettling(state.equalizer, expected)
                }
                check(levelsMatch) { "native band readback mismatch" }
            }.onFailure { failures += "$sessionId: ${it::class.simpleName}: ${it.message ?: "verification failed"}" }
        }
        return if (failures.isEmpty()) {
            BackendResult(true, Unit, "Android Equalizer retained-session readback verified.")
        } else {
            BackendResult(false, message = "Android Equalizer post-FX readback failed: ${failures.joinToString(" | ")}")
        }
    }

    @Synchronized
    fun restoreAndRelease(): BackendResult<Unit> {
        if (sessions.isEmpty()) {
            lastAppliedGains = null
            bassOverlayStrength = 0
            overlayBaseGains = null
            sharedSessionDynamicsSuspended = false
            return BackendResult(true, Unit, "Android Equalizer has no retained media-session controller to restore.")
        }
        val failures = mutableListOf<String>()
        sessions.toMap().forEach { (sessionId, state) ->
            runCatching {
                state.baselineLevels.indices.forEach { band ->
                    state.equalizer.setBandLevel(band.toShort(), state.baselineLevels[band])
                }
                val status = state.equalizer.setEnabled(state.baselineEnabled)
                check(status == 0) { "setEnabled restore failed: $status" }
                check(nativeLevelsMatch(state.equalizer, state.baselineLevels)) { "baseline native band readback mismatch" }
                check(runCatching { state.equalizer.enabled }.getOrDefault(false) == state.baselineEnabled) { "baseline enabled readback mismatch" }
            }.onFailure {
                failures += "$sessionId: ${it::class.simpleName}: ${it.message ?: "restore failed"}"
            }
            runCatching { state.equalizer.release() }
        }
        sessions.clear()
        lastAppliedGains = null
        bassOverlayStrength = 0
        overlayBaseGains = null
        sharedSessionDynamicsSuspended = false
        return if (failures.isEmpty()) BackendResult(true, Unit, "Android Equalizer native session baselines restored and controllers released.")
        else BackendResult(false, message = "Android Equalizer restore was not fully verified: ${failures.joinToString(" | ")}")
    }

    @Synchronized
    fun releaseWithoutRestore() {
        sessions.values.forEach { runCatching { it.equalizer.release() } }
        sessions.clear()
        lastAppliedGains = null
        bassOverlayStrength = 0
        overlayBaseGains = null
        sharedSessionDynamicsSuspended = false
    }

    @Synchronized
    fun isActive(): Boolean = sessions.isNotEmpty() && (lastAppliedGains != null || bassOverlayStrength > 0)

    private fun firstControllableState(context: Context): SessionState? {
        sessions.values.firstOrNull { runCatching { it.equalizer.hasControl() }.getOrDefault(false) }?.let { return it }
        val discovery = AudioSessionDiscovery(context.applicationContext).discover()
        val active = discovery.sessions.filter { it > 0 }.toSet()
        releaseStale(active)
        for (sessionId in active.sorted()) {
            val state = getOrCreateState(sessionId) ?: continue
            if (runCatching { state.equalizer.hasControl() }.getOrDefault(false)) return state
        }
        return null
    }

    private fun getOrCreateState(sessionId: Int): SessionState? {
        sessions[sessionId]?.let { current ->
            if (runCatching { current.equalizer.hasControl() && current.equalizer.numberOfBands.toInt() > 0 }.getOrDefault(false)) return current
            sessions.remove(sessionId)
            runCatching { current.equalizer.release() }
        }
        val eq = runCatching { Equalizer(Int.MAX_VALUE, sessionId) }.getOrNull() ?: return null
        return try {
            check(eq.hasControl()) { "hasControl=false" }
            check(eq.numberOfBands.toInt() > 0) { "no native bands" }
            SessionState(
                equalizer = eq,
                baselineLevels = nativeLevels(eq),
                baselineEnabled = runCatching { eq.enabled }.getOrDefault(false),
            ).also { sessions[sessionId] = it }
        } catch (_: Throwable) {
            runCatching { eq.release() }
            null
        }
    }

    private fun centersHz(eq: Equalizer): IntArray = IntArray(eq.numberOfBands.toInt()) { index ->
        eq.getCenterFreq(index.toShort()) / 1000
    }

    private fun sortedNative(eq: Equalizer): Pair<IntArray, FloatArray> {
        val rows = (0 until eq.numberOfBands.toInt()).map { index ->
            (eq.getCenterFreq(index.toShort()) / 1000) to (eq.getBandLevel(index.toShort()) / 100f)
        }.sortedBy { it.first }
        return rows.map { it.first }.toIntArray() to rows.map { it.second }.toFloatArray()
    }

    private fun overlayOnlyExpectedLevels(eq: Equalizer, baselineLevels: ShortArray, strength: Int): ShortArray {
        val centers = centersHz(eq)
        val range = eq.bandLevelRange
        check(centers.size == baselineLevels.size) { "Android Equalizer baseline band count changed" }
        return ShortArray(centers.size) { band ->
            SpeakerSafeBassOverlayPolicy.overlayMilliBelsFromBaseline(
                centerHz = centers[band],
                baselineMilliBels = baselineLevels[band],
                strength = strength,
                minMilliBels = range[0],
                maxMilliBels = range[1],
            )
        }
    }

    private fun projectedMilliBels(eq: Equalizer, gains: IntArray, overlayStrength: Int = bassOverlayStrength): ShortArray {
        val centers = centersHz(eq)
        val anchorDb = FloatArray(EqSpec.UI_ANCHOR_FREQUENCIES_HZ.size) { index ->
            gains[(index * 2).coerceAtMost(18)] / EqSpec.DAP_UNITS_PER_DB.toFloat()
        }
        val projectedDb = LogFrequencyProjector.interpolate(
            sourceHz = EqSpec.UI_ANCHOR_FREQUENCIES_HZ,
            sourceDb = anchorDb,
            targetHz = centers,
        )
        val range = eq.bandLevelRange
        return ShortArray(centers.size) { band ->
            val combinedDb = projectedDb[band] + SpeakerSafeBassOverlayPolicy.boostDb(centers[band], overlayStrength)
            (combinedDb * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort()
        }
    }

    private fun nativeLevels(eq: Equalizer): ShortArray =
        ShortArray(eq.numberOfBands.toInt()) { band -> eq.getBandLevel(band.toShort()) }

    private fun nativeLevelsMatch(eq: Equalizer, expected: ShortArray): Boolean {
        if (eq.numberOfBands.toInt() != expected.size) return false
        return expected.indices.all { band -> eq.getBandLevel(band.toShort()) == expected[band] }
    }

    private fun nativeLevelsMatchAfterSettling(eq: Equalizer, expected: ShortArray): Boolean {
        SessionFxStabilityPolicy.verificationRetryDelaysMs.forEach { delayMs ->
            if (delayMs > 0L) Thread.sleep(delayMs)
            if (nativeLevelsMatch(eq, expected)) return true
        }
        return false
    }

    private fun equalizerLevelsMatch(eq: Equalizer, expected: ShortArray): Boolean {
        if (eq.numberOfBands.toInt() != expected.size) return false
        return expected.indices.all { band ->
            SessionFxStabilityPolicy.equalizerNativeReadbackMatches(
                expected = expected[band].toInt(),
                actual = eq.getBandLevel(band.toShort()).toInt(),
            )
        }
    }

    private fun equalizerLevelsMatchAfterSettling(eq: Equalizer, expected: ShortArray): Boolean {
        SessionFxStabilityPolicy.verificationRetryDelaysMs.forEach { delayMs ->
            if (delayMs > 0L) Thread.sleep(delayMs)
            if (equalizerLevelsMatch(eq, expected)) return true
        }
        return false
    }

    private fun bassOverlayLevelsMatch(eq: Equalizer, expected: ShortArray): Boolean {
        if (eq.numberOfBands.toInt() != expected.size) return false
        return expected.indices.all { band ->
            SessionFxStabilityPolicy.bassOverlayNativeReadbackMatches(
                expected = expected[band].toInt(),
                actual = eq.getBandLevel(band.toShort()).toInt(),
            )
        }
    }

    private fun bassOverlayLevelsMatchAfterSettling(eq: Equalizer, expected: ShortArray): Boolean {
        SessionFxStabilityPolicy.verificationRetryDelaysMs.forEach { delayMs ->
            if (delayMs > 0L) Thread.sleep(delayMs)
            if (bassOverlayLevelsMatch(eq, expected)) return true
        }
        return false
    }


    private fun applyLevels(eq: Equalizer, levels: ShortArray) {
        levels.indices.forEach { band -> eq.setBandLevel(band.toShort(), levels[band]) }
        val status = eq.setEnabled(true)
        check(status == 0) { "Android Equalizer setEnabled failed: $status" }
    }

    private fun releaseStale(activeSessions: Set<Int>) {
        val stale = sessions.keys.filterNot(activeSessions::contains)
        stale.forEach { sessionId -> sessions.remove(sessionId)?.equalizer?.let { runCatching { it.release() } } }
    }
}
