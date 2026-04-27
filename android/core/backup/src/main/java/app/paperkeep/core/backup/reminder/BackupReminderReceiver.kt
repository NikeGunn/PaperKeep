package app.paperkeep.core.backup.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * P4.4 — receives the AlarmManager fire and posts a notification.
 *
 * The notification action ("OPEN_BACKUP") deep-links into the app via the
 * standard launcher intent — Settings will handle navigation to the Backup
 * section internally. Tap-through is implementation-detail of the app, not
 * the receiver.
 */
class BackupReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BackupReminderScheduler.ACTION_REMINDER) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply { putExtra(EXTRA_OPEN_BACKUP, true) }
        val pi = launch?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Time to back up Paperkeep")
            .setContentText("Tap to create an encrypted backup of your scans.")
            .setAutoCancel(true)
            .apply { if (pi != null) setContentIntent(pi) }
            .build()
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notif)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Backup reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Recurring reminders to back up your Paperkeep library." },
        )
    }

    companion object {
        const val CHANNEL_ID: String = "paperkeep.backup_reminders"
        const val EXTRA_OPEN_BACKUP: String = "open_backup"
        private const val NOTIFICATION_ID: Int = 0xB4_C5_5A
    }
}
