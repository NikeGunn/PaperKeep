package app.paperkeep.feature.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncOperationTest {

    @Test
    fun `SyncOperation has non-null id by default`() {
        val op = SyncOperation(type = SyncOperationType.UPLOAD, docId = "doc1")
        assertNotNull(op.id)
    }

    @Test
    fun `two SyncOperations with default IDs are different`() {
        val op1 = SyncOperation(type = SyncOperationType.UPLOAD, docId = "doc1")
        val op2 = SyncOperation(type = SyncOperationType.UPLOAD, docId = "doc1")
        assertNotEquals("Auto-generated IDs must be unique", op1.id, op2.id)
    }

    @Test
    fun `SyncOperation roundtrip through OfflineQueue preserves all fields`() {
        val queue = OfflineQueue()
        val original = SyncOperation(
            id = "fixed-id",
            type = SyncOperationType.RENAME,
            docId = "my-doc",
            payload = "old-name -> new-name",
        )
        queue.enqueue(original)
        val recovered = queue.dequeue()
        assertEquals(original, recovered)
    }

    @Test
    fun `OfflineQueue drains in FIFO order for mixed operations`() {
        val queue = OfflineQueue()
        val ops = SyncOperationType.values().map {
            SyncOperation(type = it, docId = "doc-${it.name}")
        }
        ops.forEach { queue.enqueue(it) }
        val dequeued = ops.map { queue.dequeue()!! }
        assertEquals(ops, dequeued)
    }
}
