package com.scanvault.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ScanEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class ScanVaultDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
}
