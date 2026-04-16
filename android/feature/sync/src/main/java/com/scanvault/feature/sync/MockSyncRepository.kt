package com.scanvault.feature.sync

/**
 * In-memory [SyncRepository] — used in tests and local development.
 */
class MockSyncRepository : SyncRepository {

    private val blobs = mutableMapOf<String, ByteArray>()
    private var presignedUrlCounter = 0

    override suspend fun createBlob(docId: String): Result<String> {
        presignedUrlCounter++
        val presignedUrl = "https://s3.example.com/bucket/processing/$docId?sig=fake$presignedUrlCounter"
        blobs[docId] = ByteArray(0) // reserve slot
        return Result.success(presignedUrl)
    }

    override suspend fun downloadBlob(docId: String): Result<ByteArray> {
        val data = blobs[docId] ?: return Result.failure(NoSuchElementException("Blob not found: $docId"))
        return Result.success(data)
    }

    override suspend fun deleteBlob(docId: String): Result<Unit> {
        blobs.remove(docId)
        return Result.success(Unit)
    }

    override suspend fun renameBlob(oldName: String, newName: String): Result<Unit> {
        val data = blobs.remove(oldName) ?: return Result.failure(NoSuchElementException("Blob not found: $oldName"))
        blobs[newName] = data
        return Result.success(Unit)
    }

    fun putBlob(docId: String, data: ByteArray) { blobs[docId] = data }
    fun hasBlob(docId: String): Boolean = blobs.containsKey(docId)
}
