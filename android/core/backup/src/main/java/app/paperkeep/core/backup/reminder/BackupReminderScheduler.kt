package app.paperkeep.core.backup.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P4.4 — schedules a single repeating backup reminder via [AlarmManager].
 *
 * Pre-API-31 we use [AlarmManager.setInexactRepeating]. On API 31+ inexact-repeating
 * is the same call but the system batches more aggressively; we never request
 * exact alarms (no `SCHEDULE_EXACT_ALARM` permission — it would put us in a
 * Play Store sensitive-permission review).
 *
 * The receiver fires a notification — see [BackupReminderReceiver]. Tapping it
 * deep-links into Settings → Backup. Notifications respect [POST_NOTIFICATIONS]
 * which the user grants on first reminder via Android's runtime prompt.
 */
@Singleton
class BackupReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun apply(cadence: BackupReminderCadence) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent()
        mgr.cancel(pi)
        if (cadence == BackupReminderCadence.NEVER) return

        val firstAt = System.currentTimeMillis() + cadence.intervalMillis
        // Inexact is intentional — exact alarms require SCHEDULE_EXACT_ALARM,
        // which Play Store treats as a sensitive permission. Reminder timing
        // doesn't need second-level precision.
        mgr.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            firstAt,
            cadence.intervalMillis,
            pi,
        )
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(ACTION_REMINDER).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    companion object {
        const val ACTION_REMINDER: String = "app.paperkeep.action.BACKUP_REMINDER"
        private const val REQUEST_CODE: Int = 0xB4_C5_5A
    }
}
