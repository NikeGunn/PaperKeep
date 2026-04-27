package app.paperkeep.core.backup.format

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `manifest.json` — the first ZIP entry in every backup. Plain JSON, version-tagged
 * so future builds can detect what they're reading without decrypting page payloads.
 *
 * Integrity: every page entry carries a SHA-256 hash of its plaintext bytes. After
 * extraction Restore recomputes and compares — a mismatch means the backup ZIP was
 * tampered with even if the outer GCM tag verified (e.g. the user mutated the inner
 * ZIP after a legitimate decrypt then re-zipped).
 */
@Serializable
data class BackupManifest(
    val format: String = FORMAT,
    val schemaVersion: Int,
    val createdAtMs: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val documentCount: Int,
    val pageCount: Int,
    val pages: List<PageRef>,
) {
    @Serializable
    data class PageRef(
        val zipPath: String,
        val documentId: String,
        val pageId: String,
        val pageIndex: Int,
        /** SHA-256 hex of the plaintext bytes inside the inner ZIP entry. */
        val sha256: String,
        val sizeBytes: Long,
    )

    companion object {
        const val FORMAT: String = "paperkeep.backup"
        const val MANIFEST_FILENAME: String = "manifest.json"
        const val DB_FILENAME: String = "paperkeep.db"
        const val SETTINGS_FILENAME: String = "settings.json"

        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(m: BackupManifest): String = json.encodeToString(serializer(), m)
        fun decode(s: String): BackupManifest = json.decodeFromString(serializer(), s)
    }
}

/**
 * Settings snapshot — what we restore alongside the data. Add fields freely;
 * future restore code uses [Json.ignoreUnknownKeys] so older builds do not
 * crash on newer payloads.
 */
@Serializable
data class BackupSettings(
    val biometricLockEnabled: Boolean = false,
    val lockTimeoutKey: String = "IMMEDIATE",
    val screenshotProtectionEnabled: Boolean = true,
    val backupReminderCadenceKey: String = "NEVER",
) {
    companion object {
        private val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(s: BackupSettings): String = json.encodeToString(serializer(), s)
        fun decode(s: String): BackupSettings = json.decodeFromString(serializer(), s)
    }
}
