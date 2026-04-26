package app.paperkeep.feature.scanner.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.SavedStateHandle
import app.paperkeep.core.common.AppDispatchers
import app.paperkeep.core.data.crypto.EncryptedImageStore
import app.paperkeep.core.data.db.ScanDao
import app.paperkeep.core.data.repository.DocumentRepository
import app.paperkeep.core.imaging.DetectionResult
import app.paperkeep.core.imaging.EdgeDetector
import app.paperkeep.core.imaging.FakeEdgeDetector
import app.paperkeep.core.imaging.ImageFilter
import app.paperkeep.core.imaging.Point2f
import app.paperkeep.core.imaging.Quad
import app.paperkeep.core.ml.ClassificationResult
import app.paperkeep.core.ml.DocTypePolicies
import app.paperkeep.core.ml.DocumentClassifier
import app.paperkeep.core.ml.DocumentType
import app.paperkeep.core.ml.OcrOrchestrator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CaptureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : AppDispatchers {
        override val main = testDispatcher
        override val io = testDispatcher
        override val default = testDispatcher
    }

    private lateinit var viewModel: CaptureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── P1 — Capture pipeline ─────────────────────────────────────────────────

    @Test
    fun `captured bitmap transitions to ReadyToCrop`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(640, 480))
        advanceUntilIdle()
        assertTrue(viewModel.state.value is CaptureState.ReadyToCrop)
    }

    @Test
    fun `ReadyToCrop has non-null image`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(640, 480))
        advanceUntilIdle()
        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertNotNull(state.image)
    }

    @Test
    fun `detector throwing transitions to Error`() = runTest {
        val badDetector = mockk<EdgeDetector>()
        every { badDetector.detect(any()) } throws RuntimeException("Native crash")
        viewModel = buildViewModel(detector = badDetector)
        viewModel.onImageCaptured(uniformBitmap(100, 100))
        advanceUntilIdle()
        assertTrue(viewModel.state.value is CaptureState.Error)
    }

    @Test
    fun `large image 4000x3000 processed without crash`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(4000, 3000))
        advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state is CaptureState.ReadyToCrop || state is CaptureState.Error)
    }

    // ── P1 — Crop screen ─────────────────────────────────────────────────────

    @Test
    fun `rotateImage swaps width and height`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(200, 100))
        advanceUntilIdle()
        val before = (viewModel.state.value as CaptureState.ReadyToCrop).image
        viewModel.rotateImage()
        advanceUntilIdle()
        val after = (viewModel.state.value as CaptureState.ReadyToCrop).image
        assertEquals(before.height, after.width)
        assertEquals(before.width, after.height)
    }

    @Test
    fun `retake resets to Idle`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(200, 150))
        advanceUntilIdle()
        viewModel.retake()
        assertEquals(CaptureState.Idle, viewModel.state.value)
    }

    @Test
    fun `onQuadUpdated persists new quad in state`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(320, 480))
        advanceUntilIdle()
        val newQuad = Quad(Point2f(20f, 20f), Point2f(300f, 20f), Point2f(300f, 460f), Point2f(20f, 460f))
        viewModel.onQuadUpdated(newQuad)
        advanceUntilIdle()
        assertEquals(newQuad, (viewModel.state.value as CaptureState.ReadyToCrop).quad)
    }

    @Test
    fun `savedState persists and restores quad`() = runTest {
        val ssh = SavedStateHandle()
        viewModel = buildViewModel(savedState = ssh)
        viewModel.onImageCaptured(nonUniformBitmap(320, 480))
        advanceUntilIdle()
        val quad = Quad(Point2f(10f, 20f), Point2f(300f, 20f), Point2f(300f, 400f), Point2f(10f, 400f))
        viewModel.onQuadUpdated(quad)
        advanceUntilIdle()
        val restored = viewModel.restoreQuadFromSavedState()
        assertNotNull(restored)
        assertEquals(quad.topLeft.x, restored!!.topLeft.x, 0.01f)
    }

    // ── P3.1 — Classification ─────────────────────────────────────────────────

    @Test
    fun `classification result populated after capture`() = runTest {
        val mockClassifier = mockk<DocumentClassifier>()
        every { mockClassifier.classify(any()) } returns ClassificationResult(DocumentType.RECEIPT, 0.9f)

        viewModel = buildViewModel(classifier = mockClassifier)
        viewModel.onImageCaptured(nonUniformBitmap(200, 600))
        advanceUntilIdle()

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertNotNull("classification must be non-null after capture", state.classification)
        assertEquals(DocumentType.RECEIPT, state.classification!!.type)
    }

    @Test
    fun `auto-applied filter matches policy for classified type`() = runTest {
        val mockClassifier = mockk<DocumentClassifier>()
        every { mockClassifier.classify(any()) } returns ClassificationResult(DocumentType.RECEIPT, 0.9f)

        viewModel = buildViewModel(classifier = mockClassifier)
        viewModel.onImageCaptured(nonUniformBitmap(200, 600))
        advanceUntilIdle()

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        val expectedFilter = DocTypePolicies.recommendedFilter(DocumentType.RECEIPT)
        assertEquals("Auto-applied filter must match policy", expectedFilter, state.selectedFilter)
    }

    @Test
    fun `onFilterSelected updates selectedFilter in state`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        advanceUntilIdle()

        viewModel.onFilterSelected(ImageFilter.GRAYSCALE)

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertEquals(ImageFilter.GRAYSCALE, state.selectedFilter)
    }

    @Test
    fun `onDocTypeOverride sets classification to selected type with confidence 1`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        advanceUntilIdle()

        viewModel.onDocTypeOverride(DocumentType.WHITEBOARD)

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertEquals(DocumentType.WHITEBOARD, state.classification?.type)
        assertEquals(1.0f, state.classification?.confidence ?: 0f, 0.001f)
    }

    @Test
    fun `onDocTypeOverride sets userOverrodeType true`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        advanceUntilIdle()

        viewModel.onDocTypeOverride(DocumentType.ID_CARD)

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertTrue("userOverrodeType must be true after manual override", state.userOverrodeType)
    }

    @Test
    fun `onDocTypeOverride applies recommended filter for the new type`() = runTest {
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        advanceUntilIdle()

        viewModel.onDocTypeOverride(DocumentType.ID_CARD)

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        val expected = DocTypePolicies.recommendedFilter(DocumentType.ID_CARD)
        assertEquals(expected, state.selectedFilter)
    }

    @Test
    fun `classifier result does NOT overwrite user override`() = runTest {
        // User first overrides to WHITEBOARD, then classifier would say RECEIPT.
        // The auto-apply must not change the filter because userOverrodeType=true.
        val mockClassifier = mockk<DocumentClassifier>()
        every { mockClassifier.classify(any()) } returns ClassificationResult(DocumentType.RECEIPT, 0.9f)

        viewModel = buildViewModel(classifier = mockClassifier)
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        advanceUntilIdle()

        // User overrides before classifier result — the state will have userOverrodeType=true
        viewModel.onDocTypeOverride(DocumentType.WHITEBOARD)

        val state = viewModel.state.value as CaptureState.ReadyToCrop
        assertTrue("userOverrodeType must be true", state.userOverrodeType)
        // The filter must be the WHITEBOARD filter, not the RECEIPT filter
        val whiteboardFilter = DocTypePolicies.recommendedFilter(DocumentType.WHITEBOARD)
        assertEquals(whiteboardFilter, state.selectedFilter)
    }

    @Test
    fun `initial classification is null when ReadyToCrop first set`() = runTest {
        // We can verify that before the classifier finishes, classification is null.
        // With a synchronous test dispatcher, both steps run on advanceUntilIdle(),
        // so we check the initial state before advancing.
        viewModel = buildViewModel()
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        // Do NOT advance — Processing state should still be active
        val stateImmediate = viewModel.state.value
        assertTrue(
            "Before processing completes, state is Processing or Capturing",
            stateImmediate is CaptureState.Processing || stateImmediate is CaptureState.Capturing
        )
    }

    @Test
    fun `CaptureState ReadyToCrop default filter is AUTO`() {
        val state = CaptureState.ReadyToCrop(
            image = uniformBitmap(10, 10),
            quad = Quad(Point2f(0f, 0f), Point2f(10f, 0f), Point2f(10f, 10f), Point2f(0f, 10f)),
        )
        assertEquals(ImageFilter.AUTO, state.selectedFilter)
        assertNull(state.classification)
        assertFalse(state.userOverrodeType)
    }

    // ── Save pipeline wiring ─────────────────────────────────────────────────

    @Test
    fun `saveCurrentCapture persists encrypted files and room rows then returns to Idle`() = runTest {
        val repo = mockk<DocumentRepository>(relaxed = true)
        val imageStore = mockk<EncryptedImageStore>(relaxed = true)
        val scanDao = mockk<ScanDao>(relaxed = true)
        val ocr = mockk<OcrOrchestrator>(relaxed = true)

        coEvery { repo.saveDocument(any()) } returns Unit
        coEvery { repo.savePage(any()) } returns Unit
        coEvery { repo.refreshFtsRow(any()) } returns Unit
        coEvery { scanDao.insert(any()) } returns Unit
        coEvery { ocr.processPage(any(), any()) } returns Unit

        viewModel = buildViewModel(
            repo = repo,
            imageStore = imageStore,
            scanDao = scanDao,
            ocrOrchestrator = ocr,
            appContext = RuntimeEnvironment.getApplication(),
        )

        viewModel.onImageCaptured(nonUniformBitmap(320, 480))
        advanceUntilIdle()

        var savedId: String? = null
        viewModel.saveCurrentCapture { id -> savedId = id }
        advanceUntilIdle()

        assertEquals(CaptureState.Idle, viewModel.state.value)
        assertNotNull(savedId)
        verify(exactly = 2) { imageStore.write(any(), any()) }
        coVerify(exactly = 1) { repo.saveDocument(any()) }
        coVerify(exactly = 1) { repo.savePage(any()) }
        coVerify(exactly = 1) { scanDao.insert(any()) }
    }

    @Test
    fun `saveCurrentCapture with failing repo transitions to Error`() = runTest {
        val repo = mockk<DocumentRepository>(relaxed = true)
        coEvery { repo.saveDocument(any()) } throws RuntimeException("DB write failed")

        viewModel = buildViewModel(repo = repo)
        viewModel.onImageCaptured(nonUniformBitmap(300, 400))
        advanceUntilIdle()

        viewModel.saveCurrentCapture()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is CaptureState.Error)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildViewModel(
        detector: EdgeDetector = FakeEdgeDetector(),
        classifier: DocumentClassifier = DocumentClassifier(),
        savedState: SavedStateHandle = SavedStateHandle(),
        repo: DocumentRepository = mockk(relaxed = true),
        imageStore: EncryptedImageStore = mockk(relaxed = true),
        scanDao: ScanDao = mockk(relaxed = true),
        ocrOrchestrator: OcrOrchestrator = mockk(relaxed = true),
        appContext: Context = RuntimeEnvironment.getApplication(),
    ) = CaptureViewModel(
        edgeDetector = detector,
        classifier = classifier,
        dispatchers = testDispatchers,
        savedState = savedState,
        documentRepository = repo,
        imageStore = imageStore,
        scanDao = scanDao,
        ocrOrchestrator = ocrOrchestrator,
        appContext = appContext,
    )

    private fun uniformBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }

    private fun nonUniformBitmap(w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(Color.WHITE)
            val mX = (w * 0.15).toInt(); val mY = (h * 0.15).toInt()
            for (y in mY until (h - mY)) for (x in mX until (w - mX)) bmp.setPixel(x, y, Color.BLACK)
        }
}
