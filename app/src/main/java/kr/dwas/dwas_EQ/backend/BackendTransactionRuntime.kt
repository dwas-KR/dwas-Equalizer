package kr.dwas.dwas_EQ.backend

data class BackendTransaction(
    val backend: BackendKind,
    val baseline: IntArray,
    val baselineEnabled: Boolean? = null,
) {
    fun snapshot() = BackendTransaction(backend, baseline.copyOf(), baselineEnabled)
}

object BackendTransactionRuntime {
    private var transaction: BackendTransaction? = null

    @Synchronized fun set(backend: BackendKind, baseline: IntArray, baselineEnabled: Boolean? = null) {
        require(baseline.size == 20)
        transaction = BackendTransaction(backend, baseline.copyOf(), baselineEnabled)
    }
    @Synchronized fun get(): BackendTransaction? = transaction?.snapshot()
    @Synchronized fun clear() { transaction = null }
}
