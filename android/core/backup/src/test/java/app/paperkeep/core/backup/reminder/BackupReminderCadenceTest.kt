package app.paperkeep.core.backup.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupReminderCadenceTest {

    @Test
    fun fromKey_unknown_defaultsToNever() {
        assertEquals(BackupReminderCadence.NEVER, BackupReminderCadence.fromKey(null))
        assertEquals(BackupReminderCadence.NEVER, BackupReminderCadence.fromKey("nonsense"))
    }

    @Test
    fun fromKey_roundTrips() {
        for (c in BackupReminderCadence.entries) {
            assertEquals(c, BackupReminderCadence.fromKey(c.storageKey))
        }
    }

    @Test
    fun weekly_intervalIs7Days() {
        assertEquals(7L * 24 * 60 * 60 * 1000, BackupReminderCadence.WEEKLY.intervalMillis)
    }

    @Test
    fun monthly_intervalIs30Days() {
        assertEquals(30L * 24 * 60 * 60 * 1000, BackupReminderCadence.MONTHLY.intervalMillis)
    }

    @Test
    fun never_intervalIsZero() {
        assertEquals(0L, BackupReminderCadence.NEVER.intervalMillis)
    }
}
