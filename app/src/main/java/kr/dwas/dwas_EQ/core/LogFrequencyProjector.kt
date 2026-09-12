package kr.dwas.dwas_EQ.core

import kotlin.math.ln

object LogFrequencyProjector {
    fun interpolate(sourceHz: IntArray, sourceDb: FloatArray, targetHz: IntArray): FloatArray {
        require(sourceHz.isNotEmpty()) { "sourceHz must not be empty" }
        require(sourceHz.size == sourceDb.size) { "source frequency/value sizes differ" }
        require(sourceHz.all { it > 0 }) { "frequencies must be positive" }
        require(sourceHz.indices.drop(1).all { sourceHz[it] > sourceHz[it - 1] }) { "source frequencies must be strictly increasing" }

        return FloatArray(targetHz.size) { index ->
            val hz = targetHz[index]
            require(hz > 0) { "target frequencies must be positive" }
            when {
                hz <= sourceHz.first() -> sourceDb.first()
                hz >= sourceHz.last() -> sourceDb.last()
                else -> {
                    var upper = 1
                    while (upper < sourceHz.size && sourceHz[upper] < hz) upper++
                    val lower = upper - 1
                    if (sourceHz[upper] == hz) {
                        sourceDb[upper]
                    } else {
                        val x0 = ln(sourceHz[lower].toDouble())
                        val x1 = ln(sourceHz[upper].toDouble())
                        val x = ln(hz.toDouble())
                        val t = ((x - x0) / (x1 - x0)).toFloat()
                        sourceDb[lower] + (sourceDb[upper] - sourceDb[lower]) * t
                    }
                }
            }
        }
    }
}
