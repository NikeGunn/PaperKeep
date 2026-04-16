package com.scanvault.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSyncSchedulerTest {

    @Test
    fun `schedule adds operation and returns handle`() {
        val scheduler = FakeSyncScheduler()
        val op = SyncOperation(type = SyncOperationType.UPLOAD, docId = "doc1")
        val handle = scheduler.schedule(op)
        assertTrue("Handle must be non-empty", handle.isNotEmpty())
        assertTrue(scheduler.isScheduled(handle))
    }

    @Test
    fun `scheduledHandles returns all handles`() {
        val scheduler = FakeSyncScheduler()
        val h1 = scheduler.schedule(SyncOperation(type = SyncOperationType.UPLOAD, docId = "a"))
        val h2 = scheduler.schedule(SyncOperation(type = SyncOperationType.DELETE, docId = "b"))
        val handles = scheduler.scheduledHandles()
        assertTrue(handles.contains(h1))
        assertTrue(handles.contains(h2))
    }

    @Test
    fun `cancel removes handle`() {
        val scheduler = FakeSyncScheduler()
        val op = SyncOperation(type = SyncOperationType.DOWNLOAD, docId = "doc-x")
        val handle = scheduler.schedule(op)
        scheduler.cancel(handle)
        assertFalse(scheduler.isScheduled(handle))
    }

    @Test
    fun `scheduledOperations returns all scheduled ops`() {
        val scheduler = FakeSyncScheduler()
        val op1 = SyncOperation(type = SyncOperationType.UPLOAD, docId = "d1")
        val op2 = SyncOperation(type = SyncOperationType.RENAME, docId = "d2")
        scheduler.schedule(op1)
        scheduler.schedule(op2)
        val ops = scheduler.scheduledOperations()
        assertEquals(2, ops.size)
    }

    @Test
    fun `cancel non-existent handle does not throw`() {
        val scheduler = FakeSyncScheduler()
        scheduler.cancel("no-such-handle") // must not throw
    }
}
