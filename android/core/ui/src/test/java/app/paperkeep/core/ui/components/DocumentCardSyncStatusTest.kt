package app.paperkeep.core.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.paperkeep.core.domain.model.Document
import app.paperkeep.core.domain.model.SyncStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DocumentCardSyncStatusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun makeDoc(syncStatus: SyncStatus) = Document(
        id = "1",
        title = "Test Doc",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        folderId = null,
        pageCount = 3,
        colorTag = null,
        pages = emptyList(),
        syncStatus = syncStatus,
    )

    @Test
    fun documentCard_cloudDone_showsSyncedIcon() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(SyncStatus.CLOUD_DONE),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Synced").assertIsDisplayed()
    }

    @Test
    fun documentCard_uploading_showsUploadingIcon() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(SyncStatus.UPLOADING),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Uploading").assertIsDisplayed()
    }

    @Test
    fun documentCard_pending_showsPendingIcon() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(SyncStatus.PENDING),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Pending sync").assertIsDisplayed()
    }

    @Test
    fun documentCard_localOnly_showsLocalOnlyIcon() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(SyncStatus.LOCAL_ONLY),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Local only").assertIsDisplayed()
    }

    @Test
    fun documentCard_contentDescription_includesSyncStatus() {
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(SyncStatus.CLOUD_DONE),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Document: Test Doc, 3 pages, synced")
            .assertIsDisplayed()
    }

    @Test
    fun documentCard_statusTransition_localToPending() {
        // Verify PENDING renders correctly (simulates mid-transition state)
        composeRule.setContent {
            DocumentCard(
                document = makeDoc(SyncStatus.PENDING),
                isSelected = false,
                onTap = {},
                onLongPress = {},
            )
        }
        composeRule.onNodeWithContentDescription("Pending sync").assertIsDisplayed()
    }
}
