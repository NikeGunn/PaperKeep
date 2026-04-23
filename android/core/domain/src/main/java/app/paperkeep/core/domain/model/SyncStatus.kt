package app.paperkeep.core.domain.model

/**
 * Sync state for a document. Drives the status icon shown on each library card.
 *
 * Transitions:
 *   LOCAL_ONLY → PENDING (user enabled sync / came online)
 *   PENDING    → UPLOADING (WorkManager worker started)
 *   UPLOADING  → CLOUD_DONE (upload confirmed by server)
 *   CLOUD_DONE → PENDING (document edited locally)
 */
enum class SyncStatus {
    /** Never uploaded — only exists on this device. */
    LOCAL_ONLY,

    /** Queued for upload; worker not yet started. */
    PENDING,

    /** Worker is actively uploading this document. */
    UPLOADING,

    /** All pages are confirmed uploaded and up to date on the server. */
    CLOUD_DONE,
}
