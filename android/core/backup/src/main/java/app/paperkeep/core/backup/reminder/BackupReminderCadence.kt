package app.paperkeep.core.backup.reminder

/**
 * P4.4 — how often the user wants to be reminded to back up.
 *
 * Values are persisted to DataStore by their [storageKey] so renames here are
 * safe as long as you also write a migration. Display strings live in the UI
 * layer (see `:feature:settings` BackupReminderDialog).
 */
enum class BackupReminderCadence(val storageKey: String, val intervalMillis: Long) {
    NEVER(storageKey = "NEVER", intervalMillis = 0L),
    WEEKLY(storageKey = "WEEKLY", intervalMillis = 7L * 24 * 60 * 60 * 1000),
    MONTHLY(storageKey = "MONTHLY", intervalMillis = 30L * 24 * 60 * 60 * 1000);

    companion object {
        fun fromKey(key: String?): BackupReminderCadence =
            entries.firstOrNull { it.storageKey == key } ?: NEVER
    }
}
