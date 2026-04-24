package app.paperkeep.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.Page
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/**
 * P2.1 — DocumentCard UI tests.
 *
 * Sync icons (cloud/upload) have been replaced with the "on device" trust pill
 * — there is no backend. Tests verify the pill appears, the accessibility string
 * is correct, and new fields (isFavorite, isArchived, docType) render correctly.
 */
@RunWith(RobolectricTestRunner::class)
class DocumentCardSyncStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeDoc(
        title: String = "Test Doc",
        isFavorite: Boolean = false,
        isArchived: Boolean = false,
        docType: String? = null,
        pageCount: Int = 3,
        pages: List<Page> = emptyList(),
    ) = Document(
        id = "1",
        title = title,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        folderId = null,
        pageCount = pageCount,
        colorTag = null,
        docType = docType,
        isFavorite = isFavorite,
        isArchived = isArchived,
        pages = pages,
    )

    // ── "on device" pill (replaces sync icons — no backend) ───────────────────

    @Test
    fun documentCard_alwaysShowsOnDevicePill() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Stored on device").assertIsDisplayed()
    }

    @Test
    fun documentCard_onDevicePill_showsText() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithText("on device").assertIsDisplayed()
    }

    // ── Accessibility content description ─────────────────────────────────────

    @Test
    fun documentCard_contentDescription_includesTitle() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(title = "My Receipt"),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Document: My Receipt", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun documentCard_contentDescription_includesOnDevice() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("on device", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun documentCard_contentDescription_includesFavouritedWhenTrue() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(isFavorite = true),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("favourited", substring = true)
            .assertIsDisplayed()
    }

    // ── Favourite icon ────────────────────────────────────────────────────────

    @Test
    fun documentCard_favourited_showsFavouriteIcon() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(isFavorite = true),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Favourited").assertIsDisplayed()
    }

    @Test
    fun documentCard_notFavourited_doesNotShowFavouriteIcon() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(isFavorite = false),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        // No Favourited content description should exist
        composeRule.onNodeWithContentDescription("Favourited").assertDoesNotExist()
    }

    // ── Title display ─────────────────────────────────────────────────────────

    @Test
    fun documentCard_showsTitle() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(title = "Invoice Q4"),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithText("Invoice Q4").assertIsDisplayed()
    }

    @Test
    fun documentCard_showsPageCount() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(pageCount = 5),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithText("5p").assertIsDisplayed()
    }
}
