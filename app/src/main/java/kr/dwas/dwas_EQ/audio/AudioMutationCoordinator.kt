package kr.dwas.dwas_EQ.audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AudioMutationCoordinator {
    private val mutex = Mutex()

    suspend fun <T> serialized(block: suspend () -> T): T = mutex.withLock { block() }
}
