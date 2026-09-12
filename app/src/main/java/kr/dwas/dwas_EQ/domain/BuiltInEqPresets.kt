package kr.dwas.dwas_EQ.domain

enum class BuiltInEqPresetCategory {
    BASIC,
    DWAS_RECOMMENDED,
}

enum class BuiltInEqPresetId {
    DOLBY_DEFAULT,
    SOFT_MIDS,
    BRIGHT_DETAIL,
    VOCAL_FOCUS,
    WARM_BASS,
    POWER_BASS,
    DYNAMIC_V,
    BASS_BOOST,
    VOCAL_CLARITY,
    POP,
    ROCK,
    HIP_HOP_RNB,
    DANCE_EDM,
    JAZZ,
    CLASSICAL,
    ACOUSTIC,
    HEAVY_METAL,
    K_POP,
    SPEECH_PODCAST,
}

data class BuiltInEqPreset(
    val id: BuiltInEqPresetId,
    val category: BuiltInEqPresetCategory,
    val curve: EqCurve,
)

object BuiltInEqPresets {
    private fun buildBasicPreset(id: BuiltInEqPresetId, values: List<Float>) =
        BuiltInEqPreset(id, BuiltInEqPresetCategory.BASIC, EqCurve.of(values))

    private fun buildRecommendedPreset(id: BuiltInEqPresetId, values: List<Float>) =
        BuiltInEqPreset(id, BuiltInEqPresetCategory.DWAS_RECOMMENDED, EqCurve.of(values))

    val all: List<BuiltInEqPreset> = listOf(
        buildBasicPreset(BuiltInEqPresetId.DOLBY_DEFAULT, List(EqCurve.BAND_COUNT) { 0f }),
        buildBasicPreset(BuiltInEqPresetId.POP, listOf(1f, 1.5f, 1f, 0f, 1f, 2f, 2f, 1.5f, 1f)),
        buildBasicPreset(BuiltInEqPresetId.ROCK, listOf(2.5f, 2f, 1f, 0f, -0.5f, 1f, 2f, 2.5f, 2f)),
        buildBasicPreset(BuiltInEqPresetId.JAZZ, listOf(1f, 1f, 0f, -1f, 0f, 1f, 2f, 2.5f, 2f)),
        buildBasicPreset(BuiltInEqPresetId.CLASSICAL, listOf(-1f, 0f, 0f, 0f, 0.5f, 1f, 1.5f, 2f, 2.5f)),
        buildBasicPreset(BuiltInEqPresetId.ACOUSTIC, listOf(-1f, 0f, 0.5f, 1f, 1.5f, 1.5f, 1f, 1.5f, 1f)),
        buildRecommendedPreset(BuiltInEqPresetId.SOFT_MIDS, listOf(0f, 0f, 2f, 5f, 1f, 0f, -3f, -4f, -5f)),
        buildRecommendedPreset(BuiltInEqPresetId.BRIGHT_DETAIL, listOf(-2f, -1f, -1f, 0f, 0f, 1f, 0f, 3f, 2f)),
        buildRecommendedPreset(BuiltInEqPresetId.VOCAL_FOCUS, listOf(-1f, -2f, -1f, -1f, 1f, 2f, 1f, 1f, 2f)),
        buildRecommendedPreset(BuiltInEqPresetId.WARM_BASS, listOf(3f, 3.5f, 2.5f, 0.5f, 0f, -2f, -5f, -4f, -5f)),
        buildRecommendedPreset(BuiltInEqPresetId.POWER_BASS, listOf(4.5f, 5.5f, 4.5f, 2f, 0.5f, 2f, 2f, -0.5f, -2f)),
        buildRecommendedPreset(BuiltInEqPresetId.DYNAMIC_V, listOf(4f, 3f, 1.5f, 1f, -0.5f, -5f, -3f, 3f, 2.5f)),
        buildRecommendedPreset(BuiltInEqPresetId.BASS_BOOST, listOf(4f, 3f, 2f, 1f, 0f, 0f, 0f, 0f, 0f)),
        buildRecommendedPreset(BuiltInEqPresetId.VOCAL_CLARITY, listOf(-2f, -1f, 0f, 1f, 2f, 3f, 2f, 1f, 0f)),
        buildRecommendedPreset(BuiltInEqPresetId.HIP_HOP_RNB, listOf(4f, 3.5f, 1.5f, 0f, 0f, 1f, 1.5f, 1.5f, 2f)),
        buildRecommendedPreset(BuiltInEqPresetId.DANCE_EDM, listOf(4f, 3f, 1f, 0f, 0f, 1f, 2f, 3f, 2.5f)),
        buildRecommendedPreset(BuiltInEqPresetId.HEAVY_METAL, listOf(3f, 2f, 1f, -0.5f, -1f, 1f, 2.5f, 3f, 2.5f)),
        buildRecommendedPreset(BuiltInEqPresetId.K_POP, listOf(1.5f, 1f, 0f, 0f, 0.5f, 1.5f, 2f, 1.5f, 1.5f)),
        buildRecommendedPreset(BuiltInEqPresetId.SPEECH_PODCAST, listOf(-2f, -1f, 0f, 1f, 2f, 2.5f, 1.5f, 0.5f, 0f)),
    )

    val basic: List<BuiltInEqPreset> = all.filter { it.category == BuiltInEqPresetCategory.BASIC }
    val recommended: List<BuiltInEqPreset> = all.filter { it.category == BuiltInEqPresetCategory.DWAS_RECOMMENDED }

    fun byId(id: BuiltInEqPresetId): BuiltInEqPreset? = all.firstOrNull { it.id == id }
}
