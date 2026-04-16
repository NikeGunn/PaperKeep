package com.scanvault.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Vault / Blob ──────────────────────────────────────────────────────────────

@Serializable
data class UploadUrlRequest(
    @SerialName("document_id") val documentId: String,
    @SerialName("content_length") val contentLength: Long,
    @SerialName("content_type") val contentType: String = "application/octet-stream",
)

@Serializable
data class UploadUrlResponse(
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("blob_id") val blobId: String,
)

@Serializable
data class ConfirmUploadRequest(
    @SerialName("blob_id") val blobId: String,
    @SerialName("document_id") val documentId: String,
)

@Serializable
data class DownloadUrlResponse(
    @SerialName("download_url") val downloadUrl: String,
)

@Serializable
data class SyncManifestItem(
    val id: String,
    @SerialName("document_id") val documentId: String,
    @SerialName("updated_at") val updatedAt: String,
    val checksum: String,
)

// ── Account / Session ─────────────────────────────────────────────────────────

@Serializable
data class ChangePasswordRequest(
    @SerialName("current_auth_hash") val currentAuthHash: String,
    @SerialName("new_auth_hash") val newAuthHash: String,
    @SerialName("new_auth_params") val newAuthParams: Map<String, String>,
    @SerialName("wrapped_key") val wrappedKey: String,
    @SerialName("kdf_salt") val kdfSalt: String,
    @SerialName("kdf_params") val kdfParams: Map<String, String>,
)

// ── Error ─────────────────────────────────────────────────────────────────────

@Serializable
data class ApiError(val error: String)
