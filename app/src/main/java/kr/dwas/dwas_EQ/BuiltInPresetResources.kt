package kr.dwas.dwas_EQ

import androidx.annotation.StringRes
import kr.dwas.dwas_EQ.domain.BuiltInEqPresetId

@StringRes
fun builtInPresetNameRes(id: BuiltInEqPresetId): Int = when (id) {
    BuiltInEqPresetId.DOLBY_DEFAULT -> R.string.builtin_preset_dolby_default
    BuiltInEqPresetId.SOFT_MIDS -> R.string.builtin_preset_soft_mids
    BuiltInEqPresetId.BRIGHT_DETAIL -> R.string.builtin_preset_bright_detail
    BuiltInEqPresetId.VOCAL_FOCUS -> R.string.builtin_preset_vocal_focus
    BuiltInEqPresetId.WARM_BASS -> R.string.builtin_preset_warm_bass
    BuiltInEqPresetId.POWER_BASS -> R.string.builtin_preset_power_bass
    BuiltInEqPresetId.DYNAMIC_V -> R.string.builtin_preset_dynamic_v
    BuiltInEqPresetId.BASS_BOOST -> R.string.builtin_preset_bass_boost
    BuiltInEqPresetId.VOCAL_CLARITY -> R.string.builtin_preset_vocal_clarity
    BuiltInEqPresetId.POP -> R.string.builtin_preset_pop
    BuiltInEqPresetId.ROCK -> R.string.builtin_preset_rock
    BuiltInEqPresetId.HIP_HOP_RNB -> R.string.builtin_preset_hip_hop_rnb
    BuiltInEqPresetId.DANCE_EDM -> R.string.builtin_preset_dance_edm
    BuiltInEqPresetId.JAZZ -> R.string.builtin_preset_jazz
    BuiltInEqPresetId.CLASSICAL -> R.string.builtin_preset_classical
    BuiltInEqPresetId.ACOUSTIC -> R.string.builtin_preset_acoustic
    BuiltInEqPresetId.HEAVY_METAL -> R.string.builtin_preset_heavy_metal
    BuiltInEqPresetId.K_POP -> R.string.builtin_preset_k_pop
    BuiltInEqPresetId.SPEECH_PODCAST -> R.string.builtin_preset_speech_podcast
}
