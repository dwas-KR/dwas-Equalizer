package kr.dwas.dwas_EQ.standardfx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFxBridgePolicyTest {
    @Test
    fun shellSuccessWithoutCoreFailureIsTerminal() {
        assertTrue(SessionFxBridgePolicy.isTerminalShellResult(true, emptySet()))
        assertTrue(SessionFxBridgePolicy.isTerminalShellResult(true, setOf("Preset Reverb (session 49)")))
    }

    @Test
    fun coreFailureRequiresLocalFallback() {
        assertFalse(SessionFxBridgePolicy.isTerminalShellResult(true, setOf("Bass Boost (session 49)")))
        assertFalse(SessionFxBridgePolicy.isTerminalShellResult(true, setOf("Virtualizer (session 49)")))
        assertFalse(SessionFxBridgePolicy.isTerminalShellResult(true, setOf("DynamicsProcessing (session 49)")))
    }

    @Test
    fun shellFailureRequiresLocalFallback() {
        assertFalse(SessionFxBridgePolicy.isTerminalShellResult(false, emptySet()))
    }
}
