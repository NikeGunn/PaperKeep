package app.paperkeep.feature.sync

import java.util.LinkedList

/**
 * In-memory FIFO queue for pending [SyncOperation]s.
 *
 * Thread-safety: not required — callers use coroutines dispatched on a single thread.
 */
class OfflineQueue {

    private val queue: LinkedList<SyncOperation> = LinkedList()

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()

    /** Add [operation] to the tail of the queue. */
    fun enqueue(operation: SyncOperation) {
        queue.addLast(operation)
    }

    /** Remove and return the head of the queue, or null if empty. */
    fun dequeue(): SyncOperation? = if (queue.isEmpty()) null else queue.removeFirst()

    /** Peek at the head without removing. */
    fun peek(): SyncOperation? = queue.peekFirst()

    /** Return all queued operations without removing them. */
    fun snapshot(): List<SyncOperation> = queue.toList()

    /** Remove operation with [id] regardless of position. */
    fun remove(id: String): Boolean = queue.removeIf { it.id == id }
}
