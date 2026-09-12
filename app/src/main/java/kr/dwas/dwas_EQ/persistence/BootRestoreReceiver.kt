package kr.dwas.dwas_EQ.persistence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import kr.dwas.dwas_EQ.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager != null && !userManager.isSystemUser) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = AppPreferences(appContext).settings.first()
                if (PersistentRestorePolicy.shouldStartAtBoot(
                        bootPersistenceEnabled = true,
                        dolbyArmed = settings.persistentEqArmed,
                        standardFxEnabled = settings.eqEnabled && settings.standardFx.requiresRuntimeHold,
                        androidEqEnabled = settings.eqEnabled && settings.androidEqArmed,
                    )
                ) {
                    PersistentDolbyService.start(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
