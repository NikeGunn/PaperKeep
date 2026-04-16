package com.scanvault.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineQueueTest {

    private fun op(type: SyncOperationType, docId: String = "doc1") =
        SyncOperation(type = type, docId = docId)

    @Test
    fun `newly created queue is empty`() {
        val queue = OfflineQueue()
        assertTrue(queue.isEmpty)
        assertEquals(0, queue.size)
    }

    @Test
    fun `enqueue increases size`() {
        val queue = OfflineQueue()
        queue.enqueue(op(SyncOperationType.UPLOAD))
        assertEquals(1, queue.size)
    }

    @Test
    fun `dequeue returns items in FIFO order`() {
        val queue = OfflineQueue()
        val op1 = op(SyncOperationType.UPLOAD, "doc1")
        val op2 = op(SyncOperationType.DOWNLOAD, "doc2")
        val op3 = op(SyncOperationType.DELETE, "doc3")

        queue.enqueue(op1)
        queue.enqueue(op2)
        queue.enqueue(op3)

        assertEquals(op1, queue.dequeue())
        assertEquals(op2, queue.dequeue())
        assertEquals(op3, queue.dequeue())
    }

    @Test
    fun `dequeue on empty queue returns null`() {
        val queue = OfflineQueue()
        assertNull(queue.dequeue())
    }

    @Test
    fun `enqueue then dequeue all leaves queue empty`() {
        val queue = OfflineQueue()
        queue.enqueue(op(SyncOperationType.UPLOAD))
        queue.dequeue()
        assertTrue(queue.isEmpty)
    }

    @Test
    fun `peek returns head without removing`() {
        val queue = OfflineQueue()
        val operation = op(SyncOperationType.RENAME, "doc42")
        queue.enqueue(operation)
        val peeked = queue.peek()
        assertEquals(operation, peeked)
        assertEquals(1, queue.size) // still in queue
    }

    @Test
    fun `snapshot returns all items in order`() {
        val queue = OfflineQueue()
        val ops = listOf(
            op(SyncOperationType.UPLOAD, "a"),
            op(SyncOperationType.DELETE, "b"),
        )
        ops.forEach { queue.enqueue(it) }
        assertEquals(ops, queue.snapshot())
    }

    @Test
    fun `remove by id removes correct item`() {
        val queue = OfflineQueue()
        val op1 = op(SyncOperationType.UPLOAD, "doc1")
        val op2 = op(SyncOperationType.DOWNLOAD, "doc2")
        queue.enqueue(op1)
        queue.enqueue(op2)

        val removed = queue.remove(op1.id)
        assertTrue(removed)
        assertEquals(1, queue.size)
        assertEquals(op2, queue.dequeue())
    }

    @Test
    fun `remove non-existent id returns false`() {
        val queue = OfflineQueue()
        assertFalse(queue.remove("non-existent-id"))
    }
}
