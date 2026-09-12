package kr.dwas.dwas_EQ.standardfx

data class DwasEnvironmentalReverbSettings(
    val roomLevelMb: Short,
    val roomHfLevelMb: Short,
    val decayTimeMs: Int,
    val decayHfRatioPermille: Short,
    val reflectionsLevelMb: Short,
    val reflectionsDelayMs: Int,
    val reverbLevelMb: Short,
    val reverbDelayMs: Int,
    val diffusionPermille: Short,
    val densityPermille: Short,
)

object DwasEnvironmentalReverbPresetPolicy {
    fun settings(preset: ReverbPreset): DwasEnvironmentalReverbSettings = when (preset) {
        ReverbPreset.NONE -> DwasEnvironmentalReverbSettings(-6000, -4000, 100, 100, -6000, 0, -6000, 0, 0, 0)
        ReverbPreset.SMALL_ROOM -> DwasEnvironmentalReverbSettings(-900, -700, 750, 700, -1400, 8, -650, 16, 780, 780)
        ReverbPreset.MEDIUM_ROOM -> DwasEnvironmentalReverbSettings(-800, -650, 1300, 720, -1250, 12, -500, 22, 830, 840)
        ReverbPreset.LARGE_ROOM -> DwasEnvironmentalReverbSettings(-750, -600, 2100, 750, -1100, 18, -400, 30, 880, 900)
        ReverbPreset.MEDIUM_HALL -> DwasEnvironmentalReverbSettings(-700, -550, 2800, 780, -1000, 22, -300, 36, 920, 930)
        ReverbPreset.LARGE_HALL -> DwasEnvironmentalReverbSettings(-650, -500, 4200, 820, -900, 28, -200, 42, 960, 970)
        ReverbPreset.PLATE -> DwasEnvironmentalReverbSettings(-700, -250, 2200, 900, -1200, 4, -100, 12, 1000, 1000)
    }
}
