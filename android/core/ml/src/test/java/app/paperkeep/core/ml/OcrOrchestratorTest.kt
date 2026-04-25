package app.paperkeep.core.ml

import android.graphics.Bitmap
import app.paperkeep.core.data.db.OcrStatus
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.domain.model.Page
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OcrOrchestratorTest {

    private lateinit var ocrEngine: OcrEngine
    private lateinit var repo: DocumentRepository

    @Before
    fun setUp() {
        ocrEngine = mockk()
        repo = mockk(relaxed = true)
    }

    private fun orchestratorWithBitmap(bitmap: Bitmap?): OcrOrchestrator =
        OcrOrchestrator(ocrEngine, repo, bitmapDecoder = { bitmap })

    private fun orchestratorNullBitmap(): OcrOrchestrator =
        OcrOrchestrator(ocrEngine, repo, bitmapDecoder = { null })

    // ── processPage ───────────────────────────────────────────────────────────

    @Test
    fun `processPage with null bitmap sets FAILED status`() = runTest {
        val page = buildPage("p1")
        val orchestrator = orchestratorNullBitmap()
        orchestrator.processPage(page, ByteArray(0))
        coVerify { repo.updateOcrStatus("p1", OcrStatus.FAILED, null) }
    }

    @Test
    fun `processPage ocrEngine throws sets FAILED status`() = runTest {
        val page = buildPage("p1")
        val bitmap = fakeBitmap()
        coEvery { ocrEngine.recognise(any()) } throws RuntimeException("ml crash")
        orchestratorWithBitmap(bitmap).processPage(page, ByteArray(1))
        coVerify { repo.updateOcrStatus("p1", OcrStatus.FAILED, null) }
    }

    @Test
    fun `processPage updates ocrStatus to DONE when text extracted`() = runTest {
        val page = buildPage("p1")
        val bitmap = fakeBitmap()
        coEvery { ocrEngine.recognise(any()) } returns "Invoice #123"

        orchestratorWithBitmap(bitmap).processPage(page, ByteArray(1))

        coVerify { repo.updateOcrStatus("p1", OcrStatus.DONE, "en") }
        coVerify { repo.updateOcrText("p1", "Invoice #123") }
        coVerify { repo.indexOcrForSearch("p1", "Invoice #123") }
        coVerify { repo.refreshFtsRow("doc-1") }
    }

    @Test
    fun `processPage stores null ocrText when engine returns blank`() = runTest {
        val page = buildPage("p1")
        val bitmap = fakeBitmap()
        coEvery { ocrEngine.recognise(any()) } returns ""

        orchestratorWithBitmap(bitmap).processPage(page, ByteArray(1))

        coVerify { repo.updateOcrStatus("p1", OcrStatus.DONE, null) }
        coVerify { repo.updateOcrText("p1", null) }
        coVerify(exactly = 0) { repo.indexOcrForSearch(any(), any()) }
    }

    // ── retryPendingPages ─────────────────────────────────────────────────────

    @Test
    fun `retryPendingPages skips page when provider returns null`() = runTest {
        val page = buildPage("p1")
        coEvery { repo.getPendingOcrPages(any()) } returns listOf(page)

        orchestratorNullBitmap().retryPendingPages(limit = 10) { null }

        coVerify { repo.updateOcrStatus("p1", OcrStatus.FAILED, null) }
        coVerify(exactly = 0) { repo.updateOcrText(any(), any()) }
    }

    @Test
    fun `retryPendingPages processes all returned pages`() = runTest {
        val pages = listOf(buildPage("p1"), buildPage("p2"))
        coEvery { repo.getPendingOcrPages(any()) } returns pages
        coEvery { ocrEngine.recognise(any()) } returns ""

        // Null bytes → null bitmap → FAILED for all pages
        orchestratorNullBitmap().retryPendingPages(limit = 20) { ByteArray(0) }

        coVerify { repo.updateOcrStatus("p1", OcrStatus.FAILED, null) }
        coVerify { repo.updateOcrStatus("p2", OcrStatus.FAILED, null) }
    }

    @Test
    fun `retryPendingPages with provider returning bytes runs ocr`() = runTest {
        val page = buildPage("p1")
        coEvery { repo.getPendingOcrPages(any()) } returns listOf(page)
        val bitmap = fakeBitmap()
        coEvery { ocrEngine.recognise(any()) } returns "some text"

        OcrOrchestrator(ocrEngine, repo, bitmapDecoder = { bitmap })
            .retryPendingPages(limit = 5) { ByteArray(1) }

        coVerify { repo.updateOcrStatus("p1", OcrStatus.DONE, "en") }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPage(id: String) = Page(
        id = id,
        documentId = "doc-1",
        pageIndex = 0,
        encryptedImagePath = "/data/p1.enc",
        encryptedThumbPath = "/data/p1_thumb.enc",
        ocrStatus = OcrStatus.PENDING,
        ocrLanguage = null,
        ocrText = null,
        width = 1080,
        height = 1920,
        filter = "original",
    )

    private fun fakeBitmap(): Bitmap =
        Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
}
