package com.scanvault.core.network.api

import com.scanvault.core.network.auth.TokenStore
import com.scanvault.core.network.model.ChangePasswordRequest
import com.scanvault.core.network.model.UploadUrlRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanVaultApiClientTest {

    private val baseUrl = "https://api.example.com/v1"

    private fun buildClient(mockEngine: MockEngine): HttpClient =
        HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

    private fun makeTokenStore(token: String = "test-token"): TokenStore {
        val ts = mockk<TokenStore>(relaxed = true)
        every { ts.accessToken } returns token
        every { ts.refreshToken } returns "refresh"
        return ts
    }

    @Test
    fun getUploadUrl_success_returnsUploadUrlResponse() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/v1/vault/blobs", request.url.encodedPath)
            respond(
                content = """{"upload_url":"https://s3.example.com/upload","blob_id":"b1"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.getUploadUrl(UploadUrlRequest("doc1", 1024L))

        assertTrue(result.isSuccess)
        assertEquals("https://s3.example.com/upload", result.getOrThrow().uploadUrl)
        assertEquals("b1", result.getOrThrow().blobId)
    }

    @Test
    fun getDownloadUrl_success_returnsDownloadUrl() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"download_url":"https://s3.example.com/dl"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.getDownloadUrl("blob-id-1")

        assertTrue(result.isSuccess)
        assertEquals("https://s3.example.com/dl", result.getOrThrow().downloadUrl)
    }

    @Test
    fun deleteBlob_success_returnsUnit() = runTest {
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.deleteBlob("blob-id-1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun getSyncManifest_success_returnsList() = runTest {
        val engine = MockEngine {
            respond(
                content = """[{"id":"m1","document_id":"d1","updated_at":"2026-01-01T00:00:00Z","checksum":"abc123"}]""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.getSyncManifest()

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        assertEquals("d1", result.getOrThrow()[0].documentId)
    }

    @Test
    fun changePassword_success_returnsUnit() = runTest {
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.OK)
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.changePassword(
            ChangePasswordRequest(
                currentAuthHash = "old",
                newAuthHash = "new",
                newAuthParams = mapOf("algorithm" to "pbkdf2"),
                wrappedKey = "wrapped",
                kdfSalt = "salt",
                kdfParams = mapOf("algorithm" to "pbkdf2"),
            )
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun deleteAccount_success_clearsTokenAndReturnsUnit() = runTest {
        val engine = MockEngine {
            respond(content = "", status = HttpStatusCode.NoContent)
        }

        val tokenStore = makeTokenStore()
        val client = ScanVaultApiClient(buildClient(engine), baseUrl, tokenStore)
        val result = client.deleteAccount()

        assertTrue(result.isSuccess)
        // Token cleared
        io.mockk.verify { tokenStore.clearAll() }
    }

    @Test
    fun apiCall_serverError_returnsFailure() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":"not found"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.deleteBlob("missing-blob")

        assertTrue(result.isFailure)
    }

    @Test
    fun apiCall_networkException_returnsFailure() = runTest {
        val engine = MockEngine {
            throw java.io.IOException("Connection refused")
        }

        val client = ScanVaultApiClient(buildClient(engine), baseUrl, makeTokenStore())
        val result = client.getSyncManifest()

        assertTrue(result.isFailure)
    }
}
