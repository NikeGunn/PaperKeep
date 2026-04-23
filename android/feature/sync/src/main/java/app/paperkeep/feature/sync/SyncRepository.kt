package app.paperkeep.feature.sync

/**
 * Abstraction over vault blob operations.
 * Real implementation uses Ktor + pre-signed S3 URLs; mock uses in-memory state.
 */
interface SyncRepository {
    /**
     * Request a pre-signed upload URL for [docId].
     * Calls POST /vault/blobs — returns the presigned PUT URL.
     */
    suspend fun createBlob(docId: String): Result<String>

    /** Download bytes for [docId] via pre-signed GET URL. */
    suspend fun downloadBlob(docId: String): Result<ByteArray>

    /** Delete blob for [docId]. */
    suspend fun deleteBlob(docId: String): Result<Unit>

    /** Rename blob from [oldName] to [newName]. */
    suspend fun renameBlob(oldName: String, newName: String): Result<Unit>
}
