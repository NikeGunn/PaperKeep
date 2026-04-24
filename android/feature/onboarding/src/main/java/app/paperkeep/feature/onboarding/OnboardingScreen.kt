package app.paperkeep.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Test tags
const val TAG_ONBOARDING_SCREEN = "onboarding_screen"
const val TAG_ONBOARDING_NEXT = "onboarding_next"
const val TAG_ONBOARDING_SKIP = "onboarding_skip"

// Spec §7 "Onboarding copy (v2, locked)" — do not change without updating the spec.
internal data class OnboardingPageContent(
    val title: String,
    val body: String,
    val ctaLabel: String,
)

internal val ONBOARDING_PAGES = listOf(
    OnboardingPageContent(
        title = "Paperkeep: scan anything. Nothing leaves your phone.",
        body = "Documents, receipts, IDs, whiteboards. All processed on this device. We never see your data. We don't have a server.",
        ctaLabel = "Next",
    ),
    OnboardingPageContent(
        title = "One permission. That's it.",
        body = "We need the camera to scan. We'll never ask for your contacts, location, or files.",
        ctaLabel = "Next",
    ),
    OnboardingPageContent(
        title = "You're ready.",
        body = "",
        ctaLabel = "Start scanning",
    ),
)

/**
 * Onboarding screen with 3 pages: Welcome → Permissions → Ready.
 *
 * Copy is locked per §7. Each page has a Skip button. Completion is persisted
 * in DataStore so the screen is shown exactly once.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val isCompleted by viewModel.isCompleted.collectAsStateWithLifecycle()

    LaunchedEffect(isCompleted) {
        if (isCompleted) onComplete()
    }

    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { ONBOARDING_PAGE_COUNT },
    )

    LaunchedEffect(currentPage) {
        pagerState.animateScrollToPage(currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TAG_ONBOARDING_SCREEN),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingPage(
                content = ONBOARDING_PAGES[page],
                isLastPage = page == ONBOARDING_PAGE_COUNT - 1,
                onNext = viewModel::nextPage,
                onSkip = viewModel::skip,
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    content: OnboardingPageContent,
    isLastPage: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (content.body.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = content.body,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onNext,
            modifier = Modifier
                .testTag(TAG_ONBOARDING_NEXT)
                .semantics { contentDescription = content.ctaLabel },
        ) {
            Text(content.ctaLabel)
        }
        if (!isLastPage) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier
                    .testTag(TAG_ONBOARDING_SKIP)
                    .semantics { contentDescription = "Skip onboarding" },
            ) {
                Text("Skip")
            }
        }
    }
}
