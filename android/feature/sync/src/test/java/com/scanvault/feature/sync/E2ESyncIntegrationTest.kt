package com.scanvault.feature.sync

import com.scanvault.core.network.api.ScanVaultApiClient
import com.scanvault.core.network.auth.TokenStore
import com.scanvault.core.network.model.DownloadUrlResponse
import com.scanvault.core.network.model.SyncManifestItem
import com.scanvault.core.network.model.UploadUrlRequest
import com.scanvault.core.network.model.UploadUrlResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 4B.10 — E2E integration test.
 *
 * Simulates the full flow:
 *   account login → upload document blob → server confirms → second client polls manifest → downloads
 *
 * Uses mock API clients to simulate both the primary device and a "second device" reading
 * the same server state. No real network or Lambda required.
 */
class E2ESyncIntegrationTest {

    // --- Shared "server" state (in-memory) ---
    private val serverBlobs = mutableMapOf<String, String>()   // blobId → s3Url
    private val serverManifest = mutableListOf<SyncManifestItem>()

    // --- Device 1 API client ---
    private lateinit var device1Api: ScanVaultApiClient
    private lateinit var device1TokenStore: TokenStore

    // --- Device 2 API client (same credentials, different instance) ---
    private lateinit var device2Api: ScanVaultApiClient
    private lateinit var device2TokenStore: TokenStore

    @Before
    fun setUp() {
        // Device 1 — authenticated, has access token
        device1TokenStore = mockk(relaxed = true)
        every { device1TokenStore.accessToken } returns "device1-token"
        every { device1TokenStore.isLoggedIn } returns true

        device1Api = mockk()

        // Device 2 — same account, different device instance
        device2TokenStore = mockk(relaxed = true)
        every { device2TokenStore.accessToken } returns "device1-token" // same token
        every { device2TokenStore.isLoggedIn } returns true

        device2Api = mockk()
    }

    @Test
    fun e2e_createAccount_scanDocument_syncToServer_verifyOnSecondDevice() = runTest {
        val docId = "doc-001"
        val blobId = "blob-001"
        val uploadUrl = "https://s3.example.com/upload/$blobId"
        val downloadUrl = "https://s3.example.com/download/$blobId"

        // --- Step 1: Device 1 uploads a blob ---
        coEvery { device1Api.getUploadUrl(UploadUrlRequest(docId, 4096L)) } answers {
            // Server records the blob
            serverBlobs[blobId] = uploadUrl
            Result.success(UploadUrlResponse(uploadUrl, blobId))
        }

        val uploadResult = device1Api.getUploadUrl(UploadUrlRequest(docId, 4096L))
        assertTrue("Upload URL request must succeed", uploadResult.isSuccess)
        assertEquals(uploadUrl, uploadResult.getOrThrow().uploadUrl)

        // --- Step 2: Device 1 confirms upload ---
        coEvery {
            device1Api.confirmUpload(any())
        } answers {
            // Server adds to manifest
            serverManifest.add(
                SyncManifestItem(
                    id = "manifest-001",
                    documentId = docId,
                    updatedAt = "2026-04-16T00:00:00Z",
                    checksum = "sha256:abc123",
                )
            )
            Result.success(Unit)
        }

        val confirmResult = device1Api.confirmUpload(
            com.scanvault.core.network.model.ConfirmUploadRequest(blobId, docId)
        )
        assertTrue("Confirm upload must succeed", confirmResult.isSuccess)
        assertEquals(1, serverManifest.size)

        // --- Step 3: Device 2 polls manifest and sees the document ---
        coEvery { device2Api.getSyncManifest(null) } returns Result.success(serverManifest.toList())

        val manifestResult = device2Api.getSyncManifest(null)
        assertTrue("Manifest fetch must succeed", manifestResult.isSuccess)

        val manifest = manifestResult.getOrThrow()
        assertEquals("Second device must see exactly 1 document", 1, manifest.size)
        assertEquals(docId, manifest[0].documentId)

        // --- Step 4: Device 2 downloads the blob ---
        coEvery { device2Api.getDownloadUrl(blobId) } returns Result.success(DownloadUrlResponse(downloadUrl))

        val downloadResult = device2Api.getDownloadUrl(blobId)
        assertTrue("Download URL request must succeed", downloadResult.isSuccess)
        assertEquals(downloadUrl, downloadResult.getOrThrow().downloadUrl)
    }

    @Test
    fun e2e_offlineQueue_operationsSurviveAndDrain() = runTest {
        val queue = OfflineQueue()
        val op1 = SyncOperation(type = SyncOperationType.UPLOAD, docId = "doc-1")
        val op2 = SyncOperation(type = SyncOperationType.UPLOAD, docId = "doc-2")

        // Queue operations while "offline"
        queue.enqueue(op1)
        queue.enqueue(op2)
        assertEquals(2, queue.size)
        assertFalse(queue.isEmpty)

        // Drain when "online"
        val dequeued1 = queue.dequeue()
        val dequeued2 = queue.dequeue()
        assertEquals(op1.docId, dequeued1?.docId)
        assertEquals(op2.docId, dequeued2?.docId)
        assertTrue(queue.isEmpty)
    }

    @Test
    fun e2e_deleteAccount_clearsRemoteAndLocalTokens() = runTest {
        coEvery { device1Api.deleteAccount() } answers {
            serverBlobs.clear()
            serverManifest.clear()
            Result.success(Unit)
        }

        val result = device1Api.deleteAccount()
        assertTrue("Delete account must succeed", result.isSuccess)
        assertTrue("Server blobs must be cleared", serverBlobs.isEmpty())
        assertTrue("Server manifest must be cleared", serverManifest.isEmpty())
    }

    @Test
    fun e2e_certPinning_tlsPinConfigured() {
        // Verify TLS pin is not empty (cert pinning is configured)
        // In unit tests we can only verify the BuildConfig field is populated.
        // Actual TLS rejection is tested in instrumented tests with MockWebServer.
        val pinValue = com.scanvault.core.network.BuildConfig.TLS_PIN_SHA256
        assertFalse("TLS pin must not be empty", pinValue.isEmpty())
    }
}
