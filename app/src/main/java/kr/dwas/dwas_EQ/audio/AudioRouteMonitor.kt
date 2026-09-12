package kr.dwas.dwas_EQ.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class RouteKind { INTERNAL_SPEAKER, EXTERNAL, UNKNOWN }
enum class RouteDetectionSource { ACTIVE_MEDIA, PRESENCE_FALLBACK }

data class RouteState(
    val kind: RouteKind,
    val description: String,
    val detectionSource: RouteDetectionSource = RouteDetectionSource.PRESENCE_FALLBACK,
    val deviceTypes: List<Int> = emptyList(),
) {
    val dolbyWriteSafeByDefault: Boolean get() = kind == RouteKind.INTERNAL_SPEAKER
}

class AudioRouteMonitor(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val _state = MutableStateFlow(inspect())
    val state: StateFlow<RouteState> = _state

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    init { audioManager.registerAudioDeviceCallback(callback, null) }

    fun refresh() { _state.value = inspect() }
    fun close() { runCatching { audioManager.unregisterAudioDeviceCallback(callback) } }

    private fun inspect(): RouteState {
        val active = runCatching { audioManager.getAudioDevicesForAttributes(mediaAttributes).toList() }
            .getOrDefault(emptyList())
        if (active.isNotEmpty()) {
            val activeTypes = active.map { it.type }
            if (!shouldUsePresenceFallbackForActiveTypes(activeTypes)) {
                return classify(active, RouteDetectionSource.ACTIVE_MEDIA)
            }
            val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
                .getOrDefault(emptyList())
                .filterNot { it.type in CAPTURE_ONLY_TYPES }
            return classify(outputs, RouteDetectionSource.PRESENCE_FALLBACK)
        }

        val outputs = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
            .getOrDefault(emptyList())
            .filterNot { it.type in CAPTURE_ONLY_TYPES }
        return classify(outputs, RouteDetectionSource.PRESENCE_FALLBACK)
    }

    private fun classify(devices: List<AudioDeviceInfo>, source: RouteDetectionSource): RouteState {
        val external = devices.filter { isExternalOutputType(it.type) }
        if (external.isNotEmpty()) {
            return RouteState(
                kind = RouteKind.EXTERNAL,
                description = external.joinToString { typeName(it.type) },
                detectionSource = source,
                deviceTypes = devices.map { it.type },
            )
        }
        if (devices.any { isInternalSpeakerType(it.type) }) {
            val suffix = if (source == RouteDetectionSource.ACTIVE_MEDIA) "active media route" else "presence fallback"
            return RouteState(
                RouteKind.INTERNAL_SPEAKER,
                "Internal speaker ($suffix)",
                source,
                devices.map { it.type },
            )
        }
        return RouteState(
            RouteKind.UNKNOWN,
            if (devices.isEmpty()) "No media output route could be confirmed" else devices.joinToString { typeName(it.type) },
            source,
            devices.map { it.type },
        )
    }

    private fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Internal speaker"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Internal speaker (safe)"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote submix capture"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> "Bluetooth LE broadcast"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI audio"
        AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_AUX_LINE -> "External line audio"
        AudioDeviceInfo.TYPE_DOCK -> "Dock audio"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
        AudioDeviceInfo.TYPE_IP -> "IP audio"
        AudioDeviceInfo.TYPE_BUS -> "Bus audio"
        AudioDeviceInfo.TYPE_FM -> "FM audio"
        else -> "Audio output ($type)"
    }

    companion object {
        private val INTERNAL_SPEAKER_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        )

        private val CAPTURE_ONLY_TYPES = setOf(
            AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
        )

        internal fun shouldUsePresenceFallbackForActiveTypes(types: List<Int>): Boolean =
            types.isNotEmpty() && types.all { it in CAPTURE_ONLY_TYPES }

        internal fun isInternalSpeakerType(type: Int): Boolean = type in INTERNAL_SPEAKER_TYPES

        internal fun isExternalOutputType(type: Int): Boolean = type in EXTERNAL_TYPES

        private val EXTERNAL_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_AUX_LINE,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_IP,
            AudioDeviceInfo.TYPE_BUS,
            AudioDeviceInfo.TYPE_FM,
        )
    }
}
