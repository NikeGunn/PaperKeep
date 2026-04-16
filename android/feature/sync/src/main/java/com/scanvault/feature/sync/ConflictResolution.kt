package com.scanvault.feature.sync

enum class ConflictChoice { SERVER, LOCAL, UNDECIDED }

/**
 * Holds state for a single document conflict.
 *
 * @param docId            document identifier
 * @param serverVersion    server version number/etag
 * @param localVersion     local version number/etag
 * @param serverTimestamp  ISO-8601 timestamp of the server copy
 * @param localTimestamp   ISO-8601 timestamp of the local copy
 */
data class ConflictResolutionState(
    val docId: String,
    val serverVersion: String,
    val localVersion: String,
    val serverTimestamp: String,
    val localTimestamp: String,
    val choice: ConflictChoice = ConflictChoice.UNDECIDED,
)
