package app.paperkeep.feature.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockSyncRepositoryTest {

    @Test
    fun `createBlob returns presigned URL`() = runTest {
        val repo = MockSyncRepository()
        val result = repo.createBlob("doc-123")
        assertTrue(result.isSuccess)
        val url = result.getOrThrow()
        assertNotNull(url)
        assertTrue("URL must contain the docId", url.contains("doc-123"))
    }

    @Test
    fun `createBlob URLs are unique per call`() = runTest {
        val repo = MockSyncRepository()
        val url1 = repo.createBlob("doc1").getOrThrow()
        val url2 = repo.createBlob("doc2").getOrThrow()
        assertFalse("Each blob must get a distinct URL", url1 == url2)
    }

    @Test
    fun `downloadBlob returns data put by putBlob`() = runTest {
        val repo = MockSyncRepository()
        val data = "hello world".toByteArray()
        repo.putBlob("doc-abc", data)
        val downloaded = repo.downloadBlob("doc-abc").getOrThrow()
        assertEquals(data.toList(), downloaded.toList())
    }

    @Test
    fun `downloadBlob on missing docId returns failure`() = runTest {
        val repo = MockSyncRepository()
        val result = repo.downloadBlob("does-not-exist")
        assertTrue(result.isFailure)
    }

    @Test
    fun `deleteBlob removes blob`() = runTest {
        val repo = MockSyncRepository()
        repo.putBlob("doc-del", byteArrayOf(1, 2, 3))
        repo.deleteBlob("doc-del")
        assertFalse(repo.hasBlob("doc-del"))
    }

    @Test
    fun `renameBlob moves data to new name`() = runTest {
        val repo = MockSyncRepository()
        val data = "content".toByteArray()
        repo.putBlob("old-name", data)

        repo.renameBlob("old-name", "new-name")

        assertFalse(repo.hasBlob("old-name"))
        assertTrue(repo.hasBlob("new-name"))
        assertEquals(data.toList(), repo.downloadBlob("new-name").getOrThrow().toList())
    }
}
