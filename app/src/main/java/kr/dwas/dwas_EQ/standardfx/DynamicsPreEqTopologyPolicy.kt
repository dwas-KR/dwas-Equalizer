package kr.dwas.dwas_EQ.standardfx

object DynamicsPreEqTopologyPolicy {
    fun enabled(
        nativeBass: Boolean,
        nativeVirtualizer: Boolean,
        omitNeutralPreEq: Boolean,
    ): Boolean = nativeBass || nativeVirtualizer || !omitNeutralPreEq

    fun bandCount(
        nativeBass: Boolean,
        nativeVirtualizer: Boolean,
        omitNeutralPreEq: Boolean,
    ): Int = if (enabled(nativeBass, nativeVirtualizer, omitNeutralPreEq)) DwasNativeFxPolicy.PRE_EQ_BAND_COUNT else 0
}
