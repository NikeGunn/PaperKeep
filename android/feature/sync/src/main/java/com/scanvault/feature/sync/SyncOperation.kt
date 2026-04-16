package com.scanvault.feature.sync

import java.util.UUID

enum class SyncOperationType { UPLOAD, DOWNLOAD, DELETE, RENAME }

/**
 * A single pending sync operation held in the offline queue.
 */
data class SyncOperation(
    val id: String = UUID.randomUUID().toString(),
    val type: SyncOperationType,
    val docId: String,
    /** Optional payload — presigned URL, new name, etc. */
    val payload: String? = null,
)
