package kr.dwas.dwas_EQ.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendApplyEngineTest {
    private class FakeBackend(
        override val kind: BackendKind,
        private val probeOk: Boolean = true,
        private val writeOk: Boolean = true,
        initial: IntArray = IntArray(20),
    ) : EqBackend {
        override val supportsSafeProbe = false
        var gains = initial.copyOf()
        var writes = 0
        override fun probe() = BackendProbe(kind, probeOk, probeOk, if (probeOk) 20 else null, if (probeOk) "ok" else "unavailable")
        override fun readGains() = if (probeOk) BackendResult(true, gains.copyOf(), "read") else BackendResult(false, message = "read failed")
        override fun writeGains(gains: IntArray): BackendResult<Unit> {
            writes++
            this.gains = gains.copyOf()
            return if (writeOk) BackendResult(true, Unit, "write ok") else BackendResult(false, message = "write failed")
        }
    }

    @Test fun continuesToNextBackendWhenEarlierProbeFails() {
        val target = IntArray(20) { 8 }
        val outcome = BackendApplyEngine.apply(
            listOf(FakeBackend(BackendKind.DIRECT_DOLBY, probeOk = false), FakeBackend(BackendKind.WIRED_ADB_DOLBY)),
            target,
        ) { null }
        assertTrue(outcome.result.ok)
        assertEquals(BackendKind.WIRED_ADB_DOLBY, outcome.result.value)
    }

    @Test fun routeGateCanSkipDolbyAndReachAndroidEq() {
        val target = IntArray(20) { 8 }
        val dolby = FakeBackend(BackendKind.DIRECT_DOLBY)
        val android = FakeBackend(BackendKind.ANDROID_EQUALIZER)
        val outcome = BackendApplyEngine.apply(listOf(dolby, android), target) {
            if (it.kind == BackendKind.DIRECT_DOLBY) "route blocked" else null
        }
        assertTrue(outcome.result.ok)
        assertEquals(0, dolby.writes)
        assertEquals(BackendKind.ANDROID_EQUALIZER, outcome.result.value)
    }

    @Test fun failedWriteAttemptsRollbackBeforeFallback() {
        val target = IntArray(20) { 8 }
        val failing = FakeBackend(BackendKind.DIRECT_DOLBY, writeOk = false)
        val fallback = FakeBackend(BackendKind.ANDROID_EQUALIZER)
        val outcome = BackendApplyEngine.apply(listOf(failing, fallback), target) { null }
        assertTrue(outcome.result.ok)
        assertEquals(2, failing.writes)
        assertEquals(BackendKind.ANDROID_EQUALIZER, outcome.result.value)
    }
}
