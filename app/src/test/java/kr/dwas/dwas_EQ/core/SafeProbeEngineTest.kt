package kr.dwas.dwas_EQ.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeProbeEngineTest {
    private class Fake(private var gains: IntArray, private val corrupt: Boolean = false) : GainTransport {
        var writes = 0
        override fun readGains(): IntArray = if (corrupt && writes == 1) gains.copyOf().also { it[0] += 1 } else gains.copyOf()
        override fun writeGains(gains: IntArray) { writes++; this.gains = gains.copyOf() }
        fun current() = gains.copyOf()
    }

    @Test fun restoresOriginalAfterSuccessfulProbe() {
        val fake = Fake(IntArray(20))
        val result = SafeProbeEngine.run(fake)
        assertTrue(result.writeVerified)
        assertTrue(result.restoreVerified)
        assertArrayEquals(IntArray(20), fake.current())
    }

    @Test fun restoresOriginalEvenWhenProbeReadbackDiffers() {
        val fake = Fake(IntArray(20), corrupt = true)
        val result = SafeProbeEngine.run(fake)
        assertFalse(result.writeVerified)
        assertTrue(result.restoreVerified)
        assertArrayEquals(IntArray(20), fake.current())
    }
}
