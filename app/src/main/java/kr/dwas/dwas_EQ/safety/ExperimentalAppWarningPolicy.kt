package kr.dwas.dwas_EQ.safety

object ExperimentalAppWarningPolicy {
    fun shouldShow(preferencesReady: Boolean, acknowledged: Boolean): Boolean =
        preferencesReady && !acknowledged
}
