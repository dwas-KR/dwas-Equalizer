package kr.dwas.dwas_EQ.persistence

import kr.dwas.dwas_EQ.backend.RawAudioEffectSession
import kr.dwas.dwas_EQ.core.SafeProbeResult

object PersistentDolbyRuntime {
    private val controller = PersistentSessionController { RawAudioEffectSession.open() }

    fun applyFromUserButton(target: IntArray): PersistentSessionApplyResult = applyFromUserButton(target, null, null)

    fun applyFromUserButton(target: IntArray, baselineOverride: IntArray?): PersistentSessionApplyResult =
        applyFromUserButton(target, baselineOverride, null)

    fun applyFromUserButton(
        target: IntArray,
        baselineOverride: IntArray?,
        baselineEnabledOverride: Boolean?,
    ): PersistentSessionApplyResult {
        check(PersistentRestorePolicy.mayWrite(PersistentTrigger.USER_APPLY))
        return controller.applyAndHold(target, baselineOverride, baselineEnabledOverride)
    }

    fun activeTransaction(): ActiveEqTransaction? = controller.activeTransaction()
    fun isHoldingSession(): Boolean = controller.isHoldingSession()
    fun readHeld(): IntArray? = controller.readHeld()
    fun readHeldEnabled(): Boolean? = controller.readHeldEnabled()
    fun writeHeldEnabledAndVerify(enabled: Boolean): Boolean = controller.writeHeldEnabledAndVerify(enabled)
    fun validateHeld(): PersistentSessionValidation = controller.validateHeld()
    fun writeHeldAndVerify(target: IntArray): Boolean = controller.writeHeldAndVerify(target)
    fun safeProbeHeld(): SafeProbeResult? = controller.safeProbeHeld()
    fun restoreAndRelease(): Boolean = controller.restoreAndRelease()
    fun releaseSession() = controller.releaseSession()
    fun release() = controller.release()
}
