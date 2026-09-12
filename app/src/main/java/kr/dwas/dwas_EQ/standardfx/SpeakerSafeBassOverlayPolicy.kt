package kr.dwas.dwas_EQ.standardfx

object SpeakerSafeBassOverlayPolicy {
    fun boostDb(centerHz: Int, strength: Int): Float {
        val factor = strength.coerceIn(0, 1000) / 1000f
        val maximum = when {
            centerHz <= 80 -> 12f
            centerHz <= 160 -> 6f
            centerHz <= 315 -> 3f
            centerHz <= 630 -> 1.5f
            else -> 0f
        }
        return maximum * factor
    }

    fun overlayMilliBelsFromBaseline(
        centerHz: Int,
        baselineMilliBels: Short,
        strength: Int,
        minMilliBels: Short,
        maxMilliBels: Short,
    ): Short {
        val boostMilliBels = (boostDb(centerHz, strength) * 100f).toInt()
        return (baselineMilliBels.toInt() + boostMilliBels)
            .coerceIn(minMilliBels.toInt(), maxMilliBels.toInt())
            .toShort()
    }
}
