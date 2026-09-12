package kr.dwas.dwas_EQ

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kr.dwas.dwas_EQ.ui.AppLocaleController
import kr.dwas.dwas_EQ.ui.AppThemeController
import kr.dwas.dwas_EQ.ui.DwasEqApp
import kr.dwas.dwas_EQ.ui.theme.DwasEqTheme

class MainActivity : ComponentActivity() {
    private var notificationPermissionGranted by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocaleController.sync(this)
        notificationPermissionGranted = hasNotificationPermission()
        enableEdgeToEdge()
        setContent {
            DwasEqTheme(AppThemeController.current(this@MainActivity)) {
                DwasEqApp(
                    notificationPermissionGranted = notificationPermissionGranted,
                    onDeclineHearingSafety = ::finishAndRemoveTask,
                )
                if (!notificationPermissionGranted) {
                    MandatoryNotificationPermissionDialog(::openNotificationSettings)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppLocaleController.sync(this)
        notificationPermissionGranted = hasNotificationPermission()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }
}

@Composable
private fun MandatoryNotificationPermissionDialog(onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.notification_permission_required_title)) },
        text = { Text(stringResource(R.string.notification_permission_required_message)) },
        confirmButton = {
            Button(onClick = onOpenSettings) {
                Text(stringResource(R.string.notification_permission_settings_action))
            }
        },
    )
}
