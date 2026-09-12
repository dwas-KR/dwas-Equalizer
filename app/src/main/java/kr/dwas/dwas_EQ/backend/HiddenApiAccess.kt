package kr.dwas.dwas_EQ.backend

import org.lsposed.hiddenapibypass.HiddenApiBypass

object HiddenApiAccess {
    @Volatile
    private var cached: Result<Unit>? = null

    fun ensureEnabled(): Result<Unit> {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: runCatching {
                HiddenApiBypass.setHiddenApiExemptions("")
                Unit
            }.also { cached = it }
        }
    }
}
