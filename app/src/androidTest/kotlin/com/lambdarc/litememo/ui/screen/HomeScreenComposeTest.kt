package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.component.MemoCardTestTags
import com.lambdarc.litememo.ui.state.HomeBulkTagDialogUiState
import com.lambdarc.litememo.ui.state.HomeUiState
import com.lambdarc.litememo.ui.state.MemoSelectionUiState
import com.lambdarc.litememo.ui.state.SearchUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun boundaryEmptyMemosShowsEmptyState() {
        // Arrange
        val uiState = HomeUiState(isLoading = false)

        // Act
        composeRule.setContent {
            TestScreenContent {
                HomeScreen(
                    uiState = uiState,
                    onFilterSelect = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onMemoLongClick = {},
                    onMemoSelectionToggle = {},
                    onClearSelection = {},
                    onMoveSelectedMemosToTrash = {},
                    onSetSelectedMemosFavorite = {},
                    onRequestToggleTagForSelectedMemos = {},
                    onToggleSelectedMemosTag = {},
                    onDismissBulkTagDialog = {},
                    onShareSelectedMemo = {},
                    onMemoClick = {},
                    onCreateMemoClick = {},
                    onRetry = {}
                )
            }
        }

        // Assert
        composeRule
            .onNodeWithText(string(R.string.empty_home_title))
            .assertIsDisplayed()
    }

    @Test
    fun stateTransitionSearchQueryShowsFilteredMemo() {
        // Arrange
        val milkMemo = testMemoUiModel(id = "memo-milk", title = "Milk list")
        val tripMemo = testMemoUiModel(id = "memo-trip", title = "Trip plan")
        var uiState by mutableStateOf(
            HomeUiState(
                isLoading = false,
                memos = listOf(milkMemo, tripMemo)
            )
        )
        composeRule.setContent {
            TestScreenContent {
                HomeScreen(
                    uiState = uiState,
                    onFilterSelect = {},
                    onSearchToggle = {
                        uiState = uiState.copy(search = SearchUiState(isActive = true))
                    },
                    onSearchQueryChange = { query ->
                        uiState = uiState.copy(
                            search = uiState.search.copy(
                                query = query,
                                results = uiState.memos.filter { memo ->
                                    memo.title.contains(query, ignoreCase = true) ||
                                        memo.body.contains(query, ignoreCase = true)
                                }
                            )
                        )
                    },
                    onMemoLongClick = {},
                    onMemoSelectionToggle = {},
                    onClearSelection = {},
                    onMoveSelectedMemosToTrash = {},
                    onSetSelectedMemosFavorite = {},
                    onRequestToggleTagForSelectedMemos = {},
                    onToggleSelectedMemosTag = {},
                    onDismissBulkTagDialog = {},
                    onShareSelectedMemo = {},
                    onMemoClick = {},
                    onCreateMemoClick = {},
                    onRetry = {}
                )
            }
        }

        // Act
        // StateTransition: search text updates displayed search results.
        composeRule
            .onNodeWithContentDescription(string(R.string.search))
            .performClick()
        composeRule
            .onNode(hasSetTextAction())
            .performTextInput("milk")

        // Assert
        composeRule
            .onNodeWithText(milkMemo.title)
            .assertIsDisplayed()
        composeRule
            .onAllNodesWithText(tripMemo.title)
            .assertCountEquals(0)
    }

    @Test
    fun normalThumbnailPathShowsMemoCardThumbnail() {
        // Arrange
        val memo = testMemoUiModel(thumbnailPath = "/missing/image-1.jpg")

        // Act
        composeRule.setContent {
            TestScreenContent {
                HomeScreen(
                    uiState = HomeUiState(isLoading = false, memos = listOf(memo)),
                    onFilterSelect = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onMemoLongClick = {},
                    onMemoSelectionToggle = {},
                    onClearSelection = {},
                    onMoveSelectedMemosToTrash = {},
                    onSetSelectedMemosFavorite = {},
                    onRequestToggleTagForSelectedMemos = {},
                    onToggleSelectedMemosTag = {},
                    onDismissBulkTagDialog = {},
                    onShareSelectedMemo = {},
                    onMemoClick = {},
                    onCreateMemoClick = {},
                    onRetry = {}
                )
            }
        }

        // Assert
        composeRule
            .onNodeWithTag(MemoCardTestTags.THUMBNAIL, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun normalNullThumbnailPathHidesMemoCardThumbnail() {
        // Arrange
        val memo = testMemoUiModel(thumbnailPath = null)

        // Act
        composeRule.setContent {
            TestScreenContent {
                HomeScreen(
                    uiState = HomeUiState(isLoading = false, memos = listOf(memo)),
                    onFilterSelect = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onMemoLongClick = {},
                    onMemoSelectionToggle = {},
                    onClearSelection = {},
                    onMoveSelectedMemosToTrash = {},
                    onSetSelectedMemosFavorite = {},
                    onRequestToggleTagForSelectedMemos = {},
                    onToggleSelectedMemosTag = {},
                    onDismissBulkTagDialog = {},
                    onShareSelectedMemo = {},
                    onMemoClick = {},
                    onCreateMemoClick = {},
                    onRetry = {}
                )
            }
        }

        // Assert
        composeRule
            .onAllNodesWithTag(MemoCardTestTags.THUMBNAIL)
            .assertCountEquals(0)
    }

    @Test
    fun interactionBulkTagDialogOpens() {
        // Arrange
        val tag = testTagUiModel(id = "tag-work", name = "Work")
        val memo = testMemoUiModel(id = "memo-1", title = "Selected memo", tags = listOf(tag))
        var uiState by mutableStateOf(
            HomeUiState(
                isLoading = false,
                memos = listOf(memo),
                tags = listOf(tag),
                selection = MemoSelectionUiState(setOf(memo.id))
            )
        )
        composeRule.setContent {
            TestScreenContent {
                HomeScreen(
                    uiState = uiState,
                    onFilterSelect = {},
                    onSearchToggle = {},
                    onSearchQueryChange = {},
                    onMemoLongClick = {},
                    onMemoSelectionToggle = {},
                    onClearSelection = {},
                    onMoveSelectedMemosToTrash = {},
                    onSetSelectedMemosFavorite = {},
                    onRequestToggleTagForSelectedMemos = {
                        uiState = uiState.copy(
                            bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
                        )
                    },
                    onToggleSelectedMemosTag = {},
                    onDismissBulkTagDialog = {},
                    onShareSelectedMemo = {},
                    onMemoClick = {},
                    onCreateMemoClick = {},
                    onRetry = {}
                )
            }
        }

        // Act
        // Interaction: tapping the bulk tag action shows the tag dialog.
        composeRule
            .onNodeWithContentDescription(string(R.string.toggle_tag_for_selected_memos))
            .performClick()

        // Assert
        composeRule
            .onNodeWithText(string(R.string.toggle_tag_for_selected_memos))
            .assertIsDisplayed()
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
