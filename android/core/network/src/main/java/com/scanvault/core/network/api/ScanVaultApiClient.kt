package com.scanvault.core.network.api

import com.scanvault.core.network.auth.TokenStore
import com.scanvault.core.network.model.ApiError
import com.scanvault.core.network.model.ChangePasswordRequest
import com.scanvault.core.network.model.ConfirmUploadRequest
import com.scanvault.core.network.model.DownloadUrlResponse
import com.scanvault.core.network.model.SyncManifestItem
import com.scanvault.core.network.model.UploadUrlRequest
import com.scanvault.core.network.model.UploadUrlResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Typed Ktor client for all ScanVault API calls.
 * Token injection is handled by the HttpClient's auth plugin (in NetworkModule).
 */
@Singleton
class ScanVaultApiClient @Inject constructor(
    private val httpClient: HttpClient,
    @Named("api_base_url") private val baseUrl: String,
    private val tokenStore: TokenStore,
) {

    // ── Vault / Blobs ─────────────────────────────────────────────────────────

    /** POST /v1/vault/blobs — get pre-signed S3 upload URL. */
    suspend fun getUploadUrl(request: UploadUrlRequest): Result<UploadUrlResponse> =
        apiCall { httpClient.post("$baseUrl/vault/blobs") { json(request) }.body() }

    /** POST /v1/vault/blobs/{blobId}/confirm — confirm upload complete. */
    suspend fun confirmUpload(request: ConfirmUploadRequest): Result<Unit> =
        apiCall {
            val resp = httpClient.post("$baseUrl/vault/blobs/${request.blobId}/confirm") {
                json(request)
            }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiError>()
                error("HTTP ${resp.status.value}: ${err.error}")
            }
        }

    /** GET /v1/vault/blobs/{blobId}/download — get pre-signed S3 download URL. */
    suspend fun getDownloadUrl(blobId: String): Result<DownloadUrlResponse> =
        apiCall { httpClient.get("$baseUrl/vault/blobs/$blobId/download").body() }

    /** DELETE /v1/vault/blobs/{blobId} — soft-delete a blob. */
    suspend fun deleteBlob(blobId: String): Result<Unit> =
        apiCall {
            val resp = httpClient.delete("$baseUrl/vault/blobs/$blobId")
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiError>()
                error("HTTP ${resp.status.value}: ${err.error}")
            }
        }

    /** GET /v1/vault/manifest — fetch sync manifest (all blobs since [since]). */
    suspend fun getSyncManifest(since: String? = null): Result<List<SyncManifestItem>> =
        apiCall {
            val url = if (since != null) "$baseUrl/vault/manifest?since=$since"
            else "$baseUrl/vault/manifest"
            httpClient.get(url).body()
        }

    // ── Account management ────────────────────────────────────────────────────

    /** POST /v1/accounts/me/password — change password and re-wrap vault key. */
    suspend fun changePassword(request: ChangePasswordRequest): Result<Unit> =
        apiCall {
            val resp = httpClient.post("$baseUrl/accounts/me/password") { json(request) }
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiError>()
                error("HTTP ${resp.status.value}: ${err.error}")
            }
        }

    /** DELETE /v1/accounts/me — permanently delete account and all data. */
    suspend fun deleteAccount(): Result<Unit> =
        apiCall {
            val resp = httpClient.delete("$baseUrl/accounts/me")
            if (!resp.status.isSuccess()) {
                val err = resp.body<ApiError>()
                error("HTTP ${resp.status.value}: ${err.error}")
            }
            // Clear stored tokens immediately
            tokenStore.clearAll()
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun io.ktor.client.request.HttpRequestBuilder.json(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private inline fun <T> apiCall(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            Result.failure(e)
        }
}
