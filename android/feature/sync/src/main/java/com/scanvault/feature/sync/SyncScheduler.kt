package com.scanvault.feature.sync

/**
 * Abstraction over WorkManager scheduling — allows unit tests to run without Android context.
 */
interface SyncScheduler {
    /** Schedule a sync operation. Returns a scheduling handle (opaque ID). */
    fun schedule(operation: SyncOperation): String

    /** Cancel a previously scheduled operation by its handle. */
    fun cancel(handle: String)

    /** Return handles of all currently scheduled operations. */
    fun scheduledHandles(): List<String>
}

/**
 * Fake [SyncScheduler] backed by an in-memory list — for unit tests.
 */
class FakeSyncScheduler : SyncScheduler {

    private val scheduled = mutableMapOf<String, SyncOperation>()

    override fun schedule(operation: SyncOperation): String {
        val handle = "handle_${operation.id}"
        scheduled[handle] = operation
        return handle
    }

    override fun cancel(handle: String) {
        scheduled.remove(handle)
    }

    override fun scheduledHandles(): List<String> = scheduled.keys.toList()

    fun scheduledOperations(): List<SyncOperation> = scheduled.values.toList()
    fun isScheduled(handle: String): Boolean = scheduled.containsKey(handle)
}
