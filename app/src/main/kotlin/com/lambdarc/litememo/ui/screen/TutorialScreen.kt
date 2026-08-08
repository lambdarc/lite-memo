package com.lambdarc.litememo.ui.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.theme.LiteMemoTheme
import kotlinx.coroutines.launch

@Composable
fun TutorialScreen(onCompleteTutorial: () -> Unit, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { tutorialPageItems.size })
    val coroutineScope = rememberCoroutineScope()
    val settledPage = pagerState.settledPage
    val navigationEnabled = !pagerState.isScrollInProgress

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onCompleteTutorial) {
                    Text(text = stringResource(R.string.tutorial_skip))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                TutorialPageContent(page = tutorialPageItems[page])
            }

            TutorialNavigation(
                state = TutorialNavigationUiState(
                    currentPage = settledPage,
                    pageCount = tutorialPageItems.size,
                    enabled = navigationEnabled
                ),
                actions = TutorialNavigationActions(
                    onPreviousClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(settledPage - 1)
                        }
                    },
                    onNextClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(settledPage + 1)
                        }
                    },
                    onCompleteTutorial = onCompleteTutorial
                )
            )
        }
    }
}

@Composable
private fun TutorialPageContent(page: TutorialPageItem) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = page.icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(page.titleResId),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TutorialNavigation(
    state: TutorialNavigationUiState,
    actions: TutorialNavigationActions
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = actions.onPreviousClick,
                enabled = state.enabled && state.currentPage > 0
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.tutorial_previous_page)
                )
            }
        }

        PageIndicator(
            currentPage = state.currentPage,
            pageCount = state.pageCount
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (state.currentPage == state.pageCount - 1) {
                Button(
                    onClick = actions.onCompleteTutorial,
                    enabled = state.enabled
                ) {
                    Text(text = stringResource(R.string.tutorial_start))
                }
            } else {
                IconButton(
                    onClick = actions.onNextClick,
                    enabled = state.enabled
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.tutorial_next_page)
                    )
                }
            }
        }
    }
}

@Composable
private fun PageIndicator(currentPage: Int, pageCount: Int) {
    val indicatorDescription = stringResource(
        R.string.tutorial_page_indicator,
        currentPage + 1,
        pageCount
    )

    Row(
        modifier = Modifier.semantics {
            contentDescription = indicatorDescription
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val color = if (index == currentPage) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

private val tutorialPageItems = listOf(
    TutorialPageItem(
        icon = Icons.Default.Edit,
        titleResId = R.string.tutorial_page_welcome_title,
        bodyResId = R.string.tutorial_page_welcome_body
    ),
    TutorialPageItem(
        icon = Icons.Default.Search,
        titleResId = R.string.tutorial_page_organize_title,
        bodyResId = R.string.tutorial_page_organize_body
    ),
    TutorialPageItem(
        icon = Icons.Default.DateRange,
        titleResId = R.string.tutorial_page_calendar_title,
        bodyResId = R.string.tutorial_page_calendar_body
    )
)

@Preview(showBackground = true)
@Composable
private fun TutorialScreenPreview() {
    LiteMemoTheme {
        TutorialScreen(onCompleteTutorial = {})
    }
}

private data class TutorialPageItem(
    val icon: ImageVector,
    @get:StringRes val titleResId: Int,
    @get:StringRes val bodyResId: Int
)

private data class TutorialNavigationUiState(
    val currentPage: Int,
    val pageCount: Int,
    val enabled: Boolean
)

private data class TutorialNavigationActions(
    val onPreviousClick: () -> Unit,
    val onNextClick: () -> Unit,
    val onCompleteTutorial: () -> Unit
)
