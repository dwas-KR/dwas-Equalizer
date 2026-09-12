package kr.dwas.dwas_EQ.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import kr.dwas.dwas_EQ.R

object UpdateNotification {
    private const val CHANNEL_ID = "dwas_eq_updates"
    private const val NOTIFICATION_ID = 7301

    fun show(context: Context, update: AvailableUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.update_notification_channel_description) }
        )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.update_notification_title))
            .setContentText(context.getString(R.string.update_notification_message, update.version.toString()))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.update_notification_message, update.version.toString())))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
