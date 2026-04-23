package app.paperkeep.feature.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates offline queue draining and scheduling.
 *
 * - Operations arriving while offline go into [OfflineQueue].
 * - [drainQueue] processes queued ops (retrying with [ExponentialBackoff] on failure).
 * - [WorkManagerSyncScheduler] (real) is injected in production; [FakeSyncScheduler] in tests.
 */
@Singleton
class SyncManager @Inject constructor(
    private val repository: SyncRepository,
    private val queue: OfflineQueue,
    private val scheduler: SyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Enqueue an operation for later sync and immediately schedule it.
     */
    fun enqueue(operation: SyncOperation): String {
        queue.enqueue(operation)
        return scheduler.schedule(operation)
    }

    /**
     * Process all currently queued operations once.
     * Returns the number of successfully processed operations.
     */
    suspend fun drainQueue(): Int {
        var successCount = 0
        while (!queue.isEmpty) {
            val op = queue.dequeue() ?: break
            val result = executeOperation(op)
            if (result.isSuccess) {
                successCount++
            } else {
                // Re-enqueue on failure (WorkManager handles retry in production)
                queue.enqueue(op)
                break
            }
        }
        return successCount
    }

    private suspend fun executeOperation(op: SyncOperation): Result<Unit> = when (op.type) {
        SyncOperationType.UPLOAD -> repository.createBlob(op.docId).map { }
        SyncOperationType.DOWNLOAD -> repository.downloadBlob(op.docId).map { }
        SyncOperationType.DELETE -> repository.deleteBlob(op.docId)
        SyncOperationType.RENAME -> {
            val (from, to) = op.payload?.split("->")
                ?: return Result.failure(IllegalArgumentException("RENAME payload missing"))
            repository.renameBlob(from.trim(), to.trim())
        }
    }
}
