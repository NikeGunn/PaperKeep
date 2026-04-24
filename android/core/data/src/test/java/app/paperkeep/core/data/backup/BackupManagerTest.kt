package app.paperkeep.core.data.backup

import android.content.Context
import android.net.Uri
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.db.DocumentDao
import app.paperkeep.core.data.db.DocumentEntity
import app.paperkeep.core.data.db.DocumentWithPages
import app.paperkeep.core.data.db.PageEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var documentDao: DocumentDao
    private lateinit var imageStore: EncryptedImageStore
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        documentDao = mockk()
        imageStore = mockk()
        every { context.getExternalFilesDir("backups") } returns tempFolder.newFolder("backups")
        every { context.filesDir } returns tempFolder.root
        manager = BackupManager(context, documentDao, imageStore)
    }

    @Test
    fun `createBackup with zero documents creates valid file`() = runTest {
        every { documentDao.observeAllWithPages() } returns flowOf(emptyList())

        val result = manager.createBackup("password".toCharArray())

        assertTrue(result.file.exists())
        assertEquals(0, result.documentCount)
        assertEquals(0, result.pageCount)
        // Salt (16) + IV (16) must be present as header
        assertTrue(result.file.length() >= 32)
    }

    @Test
    fun `createBackup file name has correct prefix and extension`() = runTest {
        every { documentDao.observeAllWithPages() } returns flowOf(emptyList())

        val result = manager.createBackup("pw".toCharArray())

        assertTrue(result.file.name.startsWith("Paperkeep_Backup_"))
        assertTrue(result.file.name.endsWith(".svbak"))
    }

    @Test
    fun `createBackup counts documents and pages`() = runTest {
        val doc = DocumentWithPages(
            document = fakeDocument("uuid-1", "My Doc"),
            pages = listOf(fakePage("path1"), fakePage("path2")),
        )
        every { documentDao.observeAllWithPages() } returns flowOf(listOf(doc))
        every { imageStore.read(File(tempFolder.root, "path1")) } returns ByteArray(100) { it.toByte() }
        every { imageStore.read(File(tempFolder.root, "path2")) } returns ByteArray(200) { it.toByte() }

        val result = manager.createBackup("s3cr3t".toCharArray())

        assertEquals(1, result.documentCount)
        assertEquals(2, result.pageCount)
    }

    @Test
    fun `createBackup skips corrupted pages gracefully`() = runTest {
        val doc = DocumentWithPages(
            document = fakeDocument("uuid-2", "Doc"),
            pages = listOf(fakePage("good"), fakePage("bad")),
        )
        every { documentDao.observeAllWithPages() } returns flowOf(listOf(doc))
        every { imageStore.read(File(tempFolder.root, "good")) } returns ByteArray(50) { 0xFF.toByte() }
        every { imageStore.read(File(tempFolder.root, "bad")) } throws IllegalStateException("corrupted")

        val result = manager.createBackup("pass".toCharArray())

        assertEquals(1, result.documentCount)
        assertEquals(1, result.pageCount)
    }

    @Test
    fun `restoreBackup on file created by createBackup returns zero counts for empty backup`() = runTest {
        every { documentDao.observeAllWithPages() } returns flowOf(emptyList())
        val password = "testpass".toCharArray()
        val backupResult = manager.createBackup(password)

        val uri = mockk<Uri>()
        every { context.contentResolver } returns mockk {
            every { openInputStream(uri) } returns backupResult.file.inputStream()
        }

        val restore = manager.restoreBackup(uri, password)

        assertEquals(0, restore.documentCount)
        assertEquals(0, restore.pageCount)
    }

    @Test
    fun `restoreBackup with wrong password either throws or returns zero counts`() = runTest {
        every { documentDao.observeAllWithPages() } returns flowOf(emptyList())
        val backupResult = manager.createBackup("correct".toCharArray())

        val uri = mockk<Uri>()
        every { context.contentResolver } returns mockk {
            every { openInputStream(uri) } returns backupResult.file.inputStream()
        }

        // AES-CBC without an auth tag: wrong key produces garbage bytes.
        // ZipInputStream may throw or silently return 0 entries. Both are acceptable.
        var threw = false
        var result: RestoreResult? = null
        try {
            result = manager.restoreBackup(uri, "wrong".toCharArray())
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("Wrong password must throw or return empty", threw || result?.documentCount == 0)
    }

    @Test
    fun `restoreBackup with null stream throws IllegalArgumentException`() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver } returns mockk {
            every { openInputStream(uri) } returns null
        }

        var threw = false
        try {
            manager.restoreBackup(uri, "pw".toCharArray())
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private fun fakeDocument(id: String, title: String) = DocumentEntity(
        id = id,
        title = title,
        createdAt = 0L,
        updatedAt = 0L,
        folderId = null,
        pageCount = 0,
        colorTag = null,
    )

    private fun fakePage(imagePath: String) = PageEntity(
        id = "page-$imagePath",
        documentId = "uuid-1",
        pageIndex = 0,
        encryptedImagePath = imagePath,
        encryptedThumbPath = imagePath,
        ocrText = null,
        width = 1080,
        height = 1920,
    )
}
