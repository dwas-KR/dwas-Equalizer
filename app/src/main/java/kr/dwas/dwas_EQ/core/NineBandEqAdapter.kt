package kr.dwas.dwas_EQ.core

object NineBandEqAdapter {
    val LABELS = listOf("63", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    val FREQUENCIES_HZ = intArrayOf(63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    fun uiDbToDap(uiDb: FloatArray): IntArray {
        require(uiDb.size == FREQUENCIES_HZ.size) { "dwas_EQ requires exactly 9 editor bands" }
        val legacy = LogFrequencyProjector.interpolate(FREQUENCIES_HZ, uiDb, EqSpec.UI_ANCHOR_FREQUENCIES_HZ)
            .map(DeviceGeqMapper::quantizeDb)
            .toFloatArray()
        return DeviceGeqMapper.uiDbToDap(legacy)
    }

    fun dapToUiDb(dap: IntArray): FloatArray {
        val legacy = DeviceGeqMapper.dapToUiDb(dap)
        return LogFrequencyProjector.interpolate(EqSpec.UI_ANCHOR_FREQUENCIES_HZ, legacy, FREQUENCIES_HZ)
            .map(DeviceGeqMapper::quantizeDb)
            .toFloatArray()
    }
}
