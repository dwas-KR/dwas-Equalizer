package kr.dwas.dwas_EQ.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kr.dwas.dwas_EQ.backend.RawAudioEffectSession

data class DeviceSnapshot(
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdk: Int,
    val board: String,
    val hardware: String,
    val dolbyPackagePresent: Boolean,
    val dapDescriptorPresent: Boolean,
    val known: DeviceCompatibilityInfo?,
)

object DeviceDetector {
    fun snapshot(context: Context): DeviceSnapshot {
        val pm = context.packageManager
        val dolby = runCatching {
            pm.getPackageInfo("com.dolby.daxservice", PackageManager.PackageInfoFlags.of(0))
            true
        }.getOrDefault(false)
        val model = Build.MODEL ?: "Unknown"
        return DeviceSnapshot(
            model = model,
            device = Build.DEVICE ?: "Unknown",
            androidRelease = Build.VERSION.RELEASE ?: "Unknown",
            sdk = Build.VERSION.SDK_INT,
            board = Build.BOARD ?: "Unknown",
            hardware = Build.HARDWARE ?: "Unknown",
            dolbyPackagePresent = dolby,
            dapDescriptorPresent = RawAudioEffectSession.queryDapDescriptor() != null,
            known = DeviceCompatibilityDatabase.find(model),
        )
    }
}
