package kr.dwas.dwas_EQ.standardfx

data class StandardFxTopologyEvidence(
    val qualcomm: Boolean,
    val qtiBassProxy: Boolean,
    val qtiVirtualizerProxy: Boolean,
    val directSoftwareBass: Boolean,
    val directSoftwareVirtualizer: Boolean,
    val directSoftwareDynamics: Boolean,
    val knownFullSessionQuirk: Boolean = false,
    val knownNoDynamicsProcessingQuirk: Boolean = false,
    val knownNativeFxVerified: Boolean = false,
    val knownSpeakerSafeFourFxQuirk: Boolean = false,
    val knownPublicBassBoostRuntimeInactive: Boolean = false,
    val knownPublicVirtualizerRuntimeInactive: Boolean = false,
    val knownIsolatedSessionStrengthFx: Boolean = false,
)

object StandardFxCompatibilityPolicy {
    const val DIRECT_SOFTWARE_BASS_UUID = "8631f300-72e2-11df-b57e-0002a5d5c51b"
    const val DIRECT_SOFTWARE_VIRTUALIZER_UUID = "1d4033c0-8557-11df-9f2d-0002a5d5c51b"
    const val DIRECT_SOFTWARE_DYNAMICS_UUID = "e0e6539b-1781-7261-676f-6d7573696340"

    fun routeSessionStrengthFxWithoutDynamics(evidence: StandardFxTopologyEvidence): Boolean =
        evidence.knownNoDynamicsProcessingQuirk

    fun routeFullStandardFxToPlaybackSession(evidence: StandardFxTopologyEvidence): Boolean {
        if (routeSessionStrengthFxWithoutDynamics(evidence)) return false
        if (evidence.knownFullSessionQuirk) return true
        return evidence.qualcomm &&
            !evidence.qtiBassProxy &&
            !evidence.qtiVirtualizerProxy &&
            evidence.directSoftwareBass &&
            evidence.directSoftwareVirtualizer &&
            evidence.directSoftwareDynamics
    }
}
