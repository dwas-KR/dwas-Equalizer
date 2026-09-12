package kr.dwas.dwas_EQ.standardfx

import android.media.audiofx.AudioEffect
import android.os.Build
import kr.dwas.dwas_EQ.device.DeviceCompatibilityDatabase
import java.util.UUID

data class StandardFxCompatibilitySnapshot(
    val evidence: StandardFxTopologyEvidence,
    val presetReverbDescriptorPresent: Boolean,
    val environmentalReverbDescriptorPresent: Boolean,
) {
    val profile: AudioCompatibilityProfile
        get() = EffectRouteResolver.resolve(evidence, presetReverbDescriptorPresent, environmentalReverbDescriptorPresent)
    val directSessionStrengthRouting: Boolean
        get() = StandardFxCompatibilityPolicy.routeSessionStrengthFxWithoutDynamics(evidence)
    val fullSessionRouting: Boolean
        get() = StandardFxCompatibilityPolicy.routeFullStandardFxToPlaybackSession(evidence)
    val sessionRouting: Boolean get() = directSessionStrengthRouting || fullSessionRouting
}

object StandardFxCompatibilityDetector {
    const val DIRECT_INSERT_ENV_REVERB_UUID = "c7a511a0-a3bb-11df-860e-0002a5d5c51b"
    fun snapshot(): StandardFxCompatibilitySnapshot {
        val descriptors = runCatching { AudioEffect.queryEffects()?.toList().orEmpty() }.getOrDefault(emptyList())
        fun hasUuid(raw: String): Boolean {
            val uuid = UUID.fromString(raw)
            return descriptors.any { it.uuid == uuid }
        }
        val known = DeviceCompatibilityDatabase.findByIdentity(
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            fingerprint = Build.FINGERPRINT,
        )
        val qualcomm = Build.HARDWARE.equals("qcom", ignoreCase = true) ||
            known?.socFamily == kr.dwas.dwas_EQ.device.SocFamily.QUALCOMM
        val evidence = StandardFxTopologyEvidence(
            qualcomm = qualcomm,
            qtiBassProxy = StableBassBoostEffect.proxyDetected(),
            qtiVirtualizerProxy = StableVirtualizerEffect.proxyDetected(),
            directSoftwareBass = hasUuid(StandardFxCompatibilityPolicy.DIRECT_SOFTWARE_BASS_UUID),
            directSoftwareVirtualizer = hasUuid(StandardFxCompatibilityPolicy.DIRECT_SOFTWARE_VIRTUALIZER_UUID),
            directSoftwareDynamics = hasUuid(StandardFxCompatibilityPolicy.DIRECT_SOFTWARE_DYNAMICS_UUID),
            knownFullSessionQuirk = known?.fullSessionStandardFx == true,
            knownNoDynamicsProcessingQuirk = known?.noDynamicsProcessingStandardFx == true,
            knownNativeFxVerified = known?.nativeFxVerified == true,
            knownSpeakerSafeFourFxQuirk = known?.speakerSafeFourFx == true,
            knownPublicBassBoostRuntimeInactive = known?.publicBassBoostRuntimeInactive == true,
            knownPublicVirtualizerRuntimeInactive = known?.publicVirtualizerRuntimeInactive == true,
            knownIsolatedSessionStrengthFx = known?.isolatedSessionStrengthFx == true,
        )
        val reverbPresent = descriptors.any { it.name.contains("reverb", ignoreCase = true) }
        val environmentalReverbPresent = hasUuid(DIRECT_INSERT_ENV_REVERB_UUID) || descriptors.any { descriptor ->
            descriptor.name.contains("Environmental", ignoreCase = true) && descriptor.name.contains("Reverb", ignoreCase = true)
        }
        return StandardFxCompatibilitySnapshot(evidence, reverbPresent, environmentalReverbPresent)
    }
}
