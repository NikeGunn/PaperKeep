package app.paperkeep.feature.scanner.capture

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for 1B.14: Crop screen constants and accessibility.
 *
 * Compose UI composition tests for the actual screen require instrumented tests
 * (androidTest/) with Hilt wiring. These unit tests verify the structural
 * contracts that the UI tests depend on.
 */
class CropScreenTest {

    @Test
    fun allCornerHandleTags_areNonEmpty() {
        assertTrue(TAG_CORNER_TL.isNotBlank())
        assertTrue(TAG_CORNER_TR.isNotBlank())
        assertTrue(TAG_CORNER_BR.isNotBlank())
        assertTrue(TAG_CORNER_BL.isNotBlank())
    }

    @Test
    fun allControlTags_areNonEmpty() {
        assertTrue(TAG_ROTATE_BUTTON.isNotBlank())
        assertTrue(TAG_RETAKE_BUTTON.isNotBlank())
        assertTrue(TAG_NEXT_BUTTON.isNotBlank())
        assertTrue(TAG_CROP_SCREEN.isNotBlank())
    }

    @Test
    fun cornerHandleTags_areUnique() {
        val tags = setOf(TAG_CORNER_TL, TAG_CORNER_TR, TAG_CORNER_BR, TAG_CORNER_BL)
        assertTrue(
            "All corner handle test tags must be unique",
            tags.size == 4
        )
    }

    @Test
    fun controlTags_areUnique() {
        val tags = setOf(TAG_ROTATE_BUTTON, TAG_RETAKE_BUTTON, TAG_NEXT_BUTTON, TAG_CROP_SCREEN)
        assertTrue(
            "All control test tags must be unique",
            tags.size == 4
        )
    }
}
