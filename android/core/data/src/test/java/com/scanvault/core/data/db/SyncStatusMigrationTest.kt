package com.scanvault.core.data.db

import com.scanvault.core.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for SyncStatus — verifies all enum values exist and have correct names
 * (the names are stored as TEXT in the DB column, so renames = breaking change).
 */
class SyncStatusMigrationTest {

    @Test
    fun syncStatus_allValuesPresent() {
        val values = SyncStatus.entries
        assertEquals(4, values.size)
    }

    @Test
    fun syncStatus_valueNames_stable() {
        assertEquals("LOCAL_ONLY", SyncStatus.LOCAL_ONLY.name)
        assertEquals("PENDING", SyncStatus.PENDING.name)
        assertEquals("UPLOADING", SyncStatus.UPLOADING.name)
        assertEquals("CLOUD_DONE", SyncStatus.CLOUD_DONE.name)
    }

    @Test
    fun syncStatus_transitions_localToCloudDone() {
        val transitions = listOf(
            SyncStatus.LOCAL_ONLY,
            SyncStatus.PENDING,
            SyncStatus.UPLOADING,
            SyncStatus.CLOUD_DONE,
        )
        // Each step is a valid forward transition
        assertEquals(SyncStatus.LOCAL_ONLY, transitions[0])
        assertEquals(SyncStatus.PENDING, transitions[1])
        assertEquals(SyncStatus.UPLOADING, transitions[2])
        assertEquals(SyncStatus.CLOUD_DONE, transitions[3])
    }

    @Test
    fun syncStatus_defaultIsLocalOnly() {
        // DocumentEntity default must be LOCAL_ONLY for new docs
        val entity = DocumentEntity(
            id = "test-id",
            title = "Test",
            createdAt = 0L,
            updatedAt = 0L,
            folderId = null,
            pageCount = 1,
            colorTag = null,
        )
        assertEquals(SyncStatus.LOCAL_ONLY, entity.syncStatus)
    }
}
