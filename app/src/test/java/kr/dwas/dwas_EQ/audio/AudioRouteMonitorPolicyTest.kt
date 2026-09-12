package kr.dwas.dwas_EQ.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRouteMonitorPolicyTest {
    @Test fun remoteSubmixOnlyFallsBackToPhysicalOutputs() {
        assertTrue(AudioRouteMonitor.shouldUsePresenceFallbackForActiveTypes(listOf(AudioDeviceInfo.TYPE_REMOTE_SUBMIX)))
        assertFalse(AudioRouteMonitor.shouldUsePresenceFallbackForActiveTypes(emptyList()))
        assertFalse(AudioRouteMonitor.shouldUsePresenceFallbackForActiveTypes(listOf(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)))
    }

    @Test fun speakerSafeOutputIsInternalSpeaker() {
        assertTrue(AudioRouteMonitor.isInternalSpeakerType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertTrue(AudioRouteMonitor.isInternalSpeakerType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE))
        assertFalse(AudioRouteMonitor.isInternalSpeakerType(AudioDeviceInfo.TYPE_REMOTE_SUBMIX))
    }

    @Test fun captureOutputIsNotExternalButPhysicalExternalOutputsRemainBlocked() {
        assertFalse(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_REMOTE_SUBMIX))
        assertTrue(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertTrue(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_USB_ACCESSORY))
        assertTrue(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_HEARING_AID))
        assertTrue(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_IP))
        assertTrue(AudioRouteMonitor.isExternalOutputType(AudioDeviceInfo.TYPE_BUS))
    }
}
