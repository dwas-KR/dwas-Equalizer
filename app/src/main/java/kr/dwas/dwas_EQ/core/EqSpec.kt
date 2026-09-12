package kr.dwas.dwas_EQ.core

object EqSpec {
    val UI_BAND_LABELS = listOf("47", "234", "469", "844", "1K", "2K", "3K", "5K", "9K", "13K")
    val UI_ANCHOR_FREQUENCIES_HZ = intArrayOf(47, 234, 469, 844, 1313, 2250, 3750, 5813, 9000, 13875)
    val DAP_FREQUENCIES_HZ = intArrayOf(
        47, 141, 234, 328, 469, 656, 844, 1031, 1313, 1688,
        2250, 3000, 3750, 4688, 5813, 7125, 9000, 11250, 13875, 19688
    )
    const val MIN_DB = -6f
    const val MAX_DB = 6f
    const val STEP_DB = 0.5f
    const val DAP_UNITS_PER_DB = 16
    const val DAP_MIN_GAIN = -96
    const val DAP_MAX_GAIN = 96
    const val MUSIC_PROFILE = 2
    const val GEQ_ENABLE_PARAMETER = 106
    const val GEQ_GAINS_PARAMETER = 110
    const val AUDIO_EFFECT_DAP_SELECTOR = 5
    const val DAP_WRITE_MARKER = 0x01000000
    const val DAP_READ_MARKER = 0x01000005
    const val DAP_GAIN_COUNT = 20
    const val DAP_SCALAR_READBACK_BYTES = 12
}
