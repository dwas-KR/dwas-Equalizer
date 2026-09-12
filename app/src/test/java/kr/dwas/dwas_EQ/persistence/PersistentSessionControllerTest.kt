package kr.dwas.dwas_EQ.persistence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentSessionControllerTest {
    private class FakeSession(
        initial: IntArray,
        private val corruptReadAtWriteCount: Int? = null,
    ) : PersistentGainSession {
        override var hasControl: Boolean = true
        var gains = initial.copyOf()
        var geqEnabled = false
        var writes = 0
        var closed = false
        override fun readGains(): IntArray =
            if (corruptReadAtWriteCount != null && writes == corruptReadAtWriteCount) IntArray(20) { 99 }
            else gains.copyOf()
        override fun writeGains(gains: IntArray) { writes++; this.gains = gains.copyOf() }
        override fun readGeqEnabled(): Boolean = geqEnabled
        override fun writeGeqEnabled(enabled: Boolean) { geqEnabled = enabled }
        override fun close() { closed = true }
    }

    @Test fun baselineSurvivesSessionReleaseAndReacquire() {
        val baseline = IntArray(20) { it }
        val first = IntArray(20) { 8 }
        val second = IntArray(20) { 16 }
        var calls = 0
        val sessions = mutableListOf<FakeSession>()
        val controller = PersistentSessionController {
            calls++
            FakeSession(if (calls == 1) baseline else first).also(sessions::add)
        }

        assertTrue(controller.applyAndHold(first).ok)
        controller.releaseSession()
        assertTrue(controller.applyAndHold(second).ok)
        assertArrayEquals(baseline, controller.activeTransaction()!!.baseline)
        assertTrue(controller.restoreAndRelease())
        assertArrayEquals(baseline, sessions.last().gains)
        assertFalse(sessions.last().geqEnabled)
    }

    @Test fun failedReapplyRollsBackToPreAttemptStateAndPreservesOriginalBaseline() {
        val baseline = IntArray(20) { it }
        val first = IntArray(20) { 8 }
        val second = IntArray(20) { 16 }
        var calls = 0
        lateinit var retry: FakeSession
        val controller = PersistentSessionController {
            calls++
            if (calls == 1) FakeSession(baseline)
            else FakeSession(first, corruptReadAtWriteCount = 1).also { retry = it }
        }

        assertTrue(controller.applyAndHold(first).ok)
        controller.releaseSession()
        assertFalse(controller.applyAndHold(second).ok)
        assertTrue(retry.closed)
        assertArrayEquals(first, retry.gains)
        assertFalse(retry.geqEnabled)
        assertArrayEquals(baseline, controller.activeTransaction()!!.baseline)
        assertArrayEquals(first, controller.activeTransaction()!!.appliedTarget)
    }

    @Test fun unsafeSafeProbeReleasesHeldSession() {
        val baseline = IntArray(20)
        val target = IntArray(20) { 8 }
        lateinit var session: FakeSession
        val controller = PersistentSessionController {
            FakeSession(baseline, corruptReadAtWriteCount = 3).also { session = it }
        }
        assertTrue(controller.applyAndHold(target).ok)
        val result = controller.safeProbeHeld()
        assertNotNull(result)
        assertFalse(result!!.restoreVerified)
        assertTrue(session.closed)
        assertFalse(controller.isHoldingSession())
    }
}
