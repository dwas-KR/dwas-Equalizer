package kr.dwas.dwas_EQ.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TB520FUGlobalStereoPolicyTest {
    @Test fun keepsCapturedStereoGlobalCandidateWithoutSpatializer() {
        val tb520 = DeviceCompatibilityDatabase.find("TB520FU")!!
        assertFalse(tb520.fullSessionStandardFx)
        assertFalse(tb520.spatializerEvidence)
        assertTrue(tb520.note.contains("stereo 0x3", ignoreCase = true))
        assertTrue(tb520.note.contains("global Standard FX", ignoreCase = true))
    }

    @Test fun recordsTb378RealDeviceValidation() {
        val tb378 = DeviceCompatibilityDatabase.find("TB378FC")!!
        assertTrue(tb378.fullSessionStandardFx)
        assertTrue(tb378.note.contains("validated on real hardware", ignoreCase = true))
    }
}
