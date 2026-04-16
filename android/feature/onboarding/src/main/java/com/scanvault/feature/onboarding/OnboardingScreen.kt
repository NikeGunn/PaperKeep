package com.scanvault.feature.onboarding

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Onboarding screen with 3 pages: Welcome → Permissions → Ready (3B.12).
 *
 * Each page has a Skip button. The last page requests camera permission.
 * Completion state is persisted in DataStore.
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

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            OnboardingPage(
                page = page,
                onNext = viewModel::nextPage,
                onSkip = viewModel::skip,
            )
        }
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val title = when (page) {
        0 -> "Welcome to ScanVault"
        1 -> "Grant Permissions"
        2 -> "You're Ready!"
        else -> ""
    }
    val body = when (page) {
        0 -> "Scan, organise and export your documents — all private, all on-device."
        1 -> "ScanVault needs camera access to scan documents."
        2 -> "Start scanning your first document."
        else -> ""
    }
    val buttonLabel = if (page == ONBOARDING_PAGE_COUNT - 1) "Get Started" else "Next"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = body)
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNext) { Text(buttonLabel) }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip) { Text("Skip") }
    }
}
