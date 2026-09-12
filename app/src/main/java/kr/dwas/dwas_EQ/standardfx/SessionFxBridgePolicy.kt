package kr.dwas.dwas_EQ.standardfx

object SessionFxBridgePolicy {
    private val coreEffectNames = setOf(
        "Attenuator",
        "Channel Balance",
        "Limiter",
        "DynamicsProcessing",
        "Bass Boost",
        "Virtualizer",
    )

    fun isTerminalShellResult(ok: Boolean, failedEffectNames: Set<String>): Boolean {
        if (!ok) return false
        return failedEffectNames.none { failed ->
            coreEffectNames.any { core -> failed == core || failed.startsWith("$core (session ") }
        }
    }
}
