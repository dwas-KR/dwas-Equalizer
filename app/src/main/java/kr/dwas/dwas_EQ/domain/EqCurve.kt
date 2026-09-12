package kr.dwas.dwas_EQ.domain

import kotlin.math.ln
import kotlin.math.round


data class EqCurve private constructor(val gainsDb: List<Float>) {
    init { require(gainsDb.size == BAND_COUNT) }

    fun withBand(index: Int, gainDb: Float): EqCurve {
        require(index in 0 until BAND_COUNT)
        return of(gainsDb.toMutableList().also { it[index] = gainDb })
    }

    fun maxPositiveGainDb(): Float = gainsDb.maxOrNull()?.coerceAtLeast(0f) ?: 0f

    fun toFloatArray(): FloatArray = gainsDb.toFloatArray()

    fun formattedBandLabel(frequencyHz: Int): String = formatFrequency(frequencyHz)

    companion object {
        const val BAND_COUNT = 9
        const val MIN_DB = -6f
        const val MAX_DB = 6f
        const val STEP_DB = 0.5f
        private val CURRENT_HZ = intArrayOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        private val LEGACY_HZ = intArrayOf(47, 234, 469, 844, 1313, 2250, 3750, 5813, 9000, 13875)

        fun flat(): EqCurve = EqCurve(List(BAND_COUNT) { 0f })

        fun of(values: List<Float>): EqCurve {
            require(values.size == BAND_COUNT) { "Expected $BAND_COUNT EQ gains" }
            return EqCurve(values.map(::quantizeDb))
        }

        fun of(values: FloatArray): EqCurve = of(values.toList())

        fun fromStoredValues(values: List<Float>): EqCurve = when (values.size) {
            BAND_COUNT -> of(values)
            LEGACY_HZ.size -> fromLegacyTenBand(values)
            else -> flat()
        }

        fun fromLegacyTenBand(values: List<Float>): EqCurve {
            require(values.size == LEGACY_HZ.size) { "Expected 10 legacy EQ gains" }
            val source = values.map(::quantizeDb)
            val projected = CURRENT_HZ.map { target -> interpolateLog(LEGACY_HZ, source, target) }
            return of(projected)
        }

        fun quantizeDb(value: Float): Float =
            (round(value.coerceIn(MIN_DB, MAX_DB) / STEP_DB) * STEP_DB).coerceIn(MIN_DB, MAX_DB)

        fun formatFrequency(frequencyHz: Int): String = when {
            frequencyHz >= 1000 && frequencyHz % 1000 == 0 -> "${frequencyHz / 1000} kHz"
            frequencyHz >= 1000 -> "${frequencyHz / 1000f} kHz"
            else -> "$frequencyHz Hz"
        }

        private fun interpolateLog(sourceHz: IntArray, sourceDb: List<Float>, targetHz: Int): Float {
            if (targetHz <= sourceHz.first()) return sourceDb.first()
            if (targetHz >= sourceHz.last()) return sourceDb.last()
            var upper = 1
            while (upper < sourceHz.size && sourceHz[upper] < targetHz) upper++
            val lower = upper - 1
            if (sourceHz[upper] == targetHz) return sourceDb[upper]
            val x0 = ln(sourceHz[lower].toDouble())
            val x1 = ln(sourceHz[upper].toDouble())
            val x = ln(targetHz.toDouble())
            val t = ((x - x0) / (x1 - x0)).toFloat()
            return sourceDb[lower] + (sourceDb[upper] - sourceDb[lower]) * t
        }
    }
}
