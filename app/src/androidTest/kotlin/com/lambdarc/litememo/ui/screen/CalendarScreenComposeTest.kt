package com.lambdarc.litememo.ui.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.component.MemoCardTestTags
import com.lambdarc.litememo.ui.state.CalendarUiState
import com.lambdarc.litememo.ui.state.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class CalendarScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalSelectedDateShowsLocalizedTitle() {
        // Arrange
        val selectedDate = LocalDate.of(2026, 5, 15)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val formattedDate = selectedDate.format(
            DateTimeFormatter.ofPattern(context.getString(R.string.selected_date_format))
        )
        val expectedTitle = context.getString(
            R.string.selected_date_title_format,
            formattedDate
        )

        // Act
        // Normal: the localized title is rendered without interpreting display text as a date pattern.
        composeRule.setContent {
            TestScreenContent {
                CalendarScreen(
                    uiState = CalendarUiState.Content(
                        selectedMonth = YearMonth.from(selectedDate),
                        selectedDate = selectedDate
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDateSelect = {},
                    onCalendarExpandedToggle = {},
                    onDatePickerRequest = {},
                    onDatePickerDismiss = {},
                    onDatePick = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onRetry = {},
                    onMemoClick = {},
                    onCreateMemoClick = {}
                )
            }
        }

        // Assert
        composeRule
            .onNodeWithText(expectedTitle)
            .assertIsDisplayed()
    }

    @Test
    fun errorLoadFailureShowsRetryAndInvokesCallback() {
        // Arrange
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var retryCount = 0

        // Act
        composeRule.setContent {
            TestScreenContent {
                CalendarScreen(
                    uiState = CalendarUiState.Error(),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDateSelect = {},
                    onCalendarExpandedToggle = {},
                    onDatePickerRequest = {},
                    onDatePickerDismiss = {},
                    onDatePick = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onRetry = { retryCount += 1 },
                    onMemoClick = {},
                    onCreateMemoClick = {}
                )
            }
        }
        composeRule
            .onNodeWithText(context.getString(R.string.retry_label))
            .performClick()

        // Assert
        composeRule
            .onNodeWithText(context.getString(R.string.unknown_error))
            .assertIsDisplayed()
        assertEquals(1, retryCount)
    }

    @Test
    fun interactionUnreadableThumbnailKeepsMemoOpenable() {
        // Arrange
        val selectedDate = LocalDate.of(2026, 5, 15)
        val memo = testMemoUiModel(
            title = "Unreadable image memo",
            thumbnailPath = "/missing/unreadable-image.jpg"
        )
        var clickedMemoId: MemoId? = null

        // Act
        composeRule.setContent {
            TestScreenContent {
                CalendarScreen(
                    uiState = CalendarUiState.Content(
                        selectedMonth = YearMonth.from(selectedDate),
                        selectedDate = selectedDate,
                        memos = listOf(memo)
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDateSelect = {},
                    onCalendarExpandedToggle = {},
                    onDatePickerRequest = {},
                    onDatePickerDismiss = {},
                    onDatePick = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onRetry = {},
                    onMemoClick = { clickedMemoId = it },
                    onCreateMemoClick = {}
                )
            }
        }
        composeRule
            .onNodeWithTag(MemoCardTestTags.THUMBNAIL, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(memo.title)
            .performScrollTo()
            .performClick()

        // Assert
        assertEquals(memo.id, clickedMemoId)
    }

    @Test
    fun errorSearchFailureShowsErrorMessage() {
        // Arrange
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Act
        composeRule.setContent {
            TestScreenContent {
                CalendarScreen(
                    uiState = CalendarUiState.Content(
                        search = SearchUiState(
                            isActive = true,
                            query = "failed query",
                            hasError = true
                        )
                    ),
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDateSelect = {},
                    onCalendarExpandedToggle = {},
                    onDatePickerRequest = {},
                    onDatePickerDismiss = {},
                    onDatePick = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onRetry = {},
                    onMemoClick = {},
                    onCreateMemoClick = {}
                )
            }
        }

        // Assert
        composeRule
            .onNodeWithText(context.getString(R.string.search_error_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.search_error_body))
            .assertIsDisplayed()
    }

    @Test
    fun normalConfirmedZeroSearchHitsShowNoResultsMessage() {
        // Arrange
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Act
        // Normal: a completed search without hits explains the empty result
        setCalendarScreen(
            CalendarUiState.Content(search = SearchUiState(isActive = true, query = "missing"))
        )

        // Assert
        composeRule
            .onNodeWithText(context.getString(R.string.no_search_results_title))
            .assertIsDisplayed()
    }

    @Test
    fun boundaryAwaitingSearchResultsHideNoResultsMessage() {
        // Arrange
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Act
        // Boundary: no zero-hit message is shown before the current query resolves
        setCalendarScreen(
            CalendarUiState.Content(
                search = SearchUiState(isActive = true, query = "milk", isAwaitingResults = true)
            )
        )

        // Assert
        composeRule
            .onAllNodesWithText(context.getString(R.string.no_search_results_title))
            .assertCountEquals(0)
    }

    @Test
    fun normalInitialStatusShowsLoading() {
        // Act
        // Normal: the loading calendar state displays progress.
        setCalendarScreen(CalendarUiState.Loading)

        // Assert
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ).assertIsDisplayed()
    }

    private fun setCalendarScreen(uiState: CalendarUiState) {
        composeRule.setContent {
            TestScreenContent {
                CalendarScreen(
                    uiState = uiState,
                    onPreviousMonth = {},
                    onNextMonth = {},
                    onDateSelect = {},
                    onCalendarExpandedToggle = {},
                    onDatePickerRequest = {},
                    onDatePickerDismiss = {},
                    onDatePick = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onRetry = {},
                    onMemoClick = {},
                    onCreateMemoClick = {}
                )
            }
        }
    }

}
