package kr.dwas.dwas_EQ.core

import kotlin.math.round
import kotlin.math.roundToInt

object DeviceGeqMapper {
    fun quantizeDb(value: Float): Float {
        val clamped = value.coerceIn(EqSpec.MIN_DB, EqSpec.MAX_DB)
        return (round(clamped / EqSpec.STEP_DB) * EqSpec.STEP_DB).coerceIn(EqSpec.MIN_DB, EqSpec.MAX_DB)
    }

    fun dbToDap(value: Float): Int = scaledDbToDap(quantizeDb(value))

    private fun scaledDbToDap(value: Float): Int =
        (value * EqSpec.DAP_UNITS_PER_DB).roundToInt().coerceIn(EqSpec.DAP_MIN_GAIN, EqSpec.DAP_MAX_GAIN)

    fun dapToUiDb(dap: IntArray): FloatArray {
        require(dap.size == EqSpec.DAP_GAIN_COUNT) { "Device GEQ requires exactly 20 DAP gains" }
        return FloatArray(10) { index ->
            quantizeDb(dap[index * 2].toFloat() / EqSpec.DAP_UNITS_PER_DB)
        }
    }

    fun uiDbToDap(uiDb: FloatArray): IntArray {
        require(uiDb.size == 10) { "Device GEQ requires exactly 10 UI anchors" }
        val q = FloatArray(10) { quantizeDb(uiDb[it]) }
        val out = IntArray(EqSpec.DAP_GAIN_COUNT)
        for (i in 0 until 9) {
            out[i * 2] = scaledDbToDap(q[i])
            out[i * 2 + 1] = scaledDbToDap((q[i] + q[i + 1]) / 2f)
        }
        out[18] = scaledDbToDap(q[9])
        out[19] = scaledDbToDap(q[9])
        return out
    }
}
