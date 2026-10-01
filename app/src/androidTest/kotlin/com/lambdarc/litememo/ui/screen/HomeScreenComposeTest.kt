package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.component.MemoCardTestTags
import com.lambdarc.litememo.ui.state.HomeBulkTagDialogUiState
import com.lambdarc.litememo.ui.state.HomeUiState
import com.lambdarc.litememo.ui.state.MemoSelectionUiState
import com.lambdarc.litememo.ui.state.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        setHomeScreen(uiState = { uiState })

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
        setHomeScreen(
            uiState = { uiState },
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
            }
        )

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
        setHomeScreen(uiState = { HomeUiState(isLoading = false, memos = listOf(memo)) })

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
        setHomeScreen(uiState = { HomeUiState(isLoading = false, memos = listOf(memo)) })

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
        setHomeScreen(
            uiState = { uiState },
            onRequestToggleTagForSelectedMemos = {
                uiState = uiState.copy(
                    bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
                )
            }
        )

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

    @Test
    fun stateTransitionErrorHidesVisibleBulkTagDialog() {
        // Arrange
        var uiState by mutableStateOf(
            HomeUiState(
                isLoading = false,
                bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
            )
        )
        setHomeScreen(uiState = { uiState })

        // Act
        // StateTransition: a whole-screen error replaces the content and its dialog.
        composeRule.runOnIdle { uiState = uiState.copy(hasError = true) }

        // Assert
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.retry_label)).assertIsDisplayed()
    }

    @Test
    fun interactionBulkTagDialogSelectsTag() {
        // Arrange
        val tag = testTagUiModel(id = "tag-work", name = "Work")
        val memo = testMemoUiModel(id = "memo-1")
        var selectedTagId: TagId? = null
        val uiState = HomeUiState(
            isLoading = false,
            memos = listOf(memo),
            tags = listOf(tag),
            selection = MemoSelectionUiState(setOf(memo.id)),
            bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
        )
        setHomeScreen(
            uiState = { uiState },
            onToggleSelectedMemosTag = { selectedTagId = it }
        )

        // Act
        // Interaction: selecting a tag in the dialog forwards its ID to the callback.
        composeRule.onNode(hasText(tag.name) and hasAnyAncestor(isDialog())).performClick()

        // Assert
        composeRule.runOnIdle { assertEquals(tag.id, selectedTagId) }
    }

    @Test
    fun interactionBulkTagDialogCancelDismissesDialog() {
        // Arrange
        var dismissRequested = false
        var uiState by mutableStateOf(
            HomeUiState(
                isLoading = false,
                bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
            )
        )
        setHomeScreen(
            uiState = { uiState },
            onDismissBulkTagDialog = {
                dismissRequested = true
                uiState = uiState.copy(bulkTagDialog = HomeBulkTagDialogUiState())
            }
        )

        // Act
        // Interaction: cancel invokes dismissal and removes the dialog after the state update.
        composeRule.onNode(
            hasText(string(R.string.cancel_label)) and hasAnyAncestor(isDialog())
        ).performClick()

        // Assert
        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        composeRule.runOnIdle { assertTrue(dismissRequested) }
    }

    @Test
    fun normalSearchErrorKeepsBulkTagDialogVisible() {
        // Arrange
        val memo = testMemoUiModel(id = "memo-1")
        val uiState = HomeUiState(
            isLoading = false,
            memos = listOf(memo),
            search = SearchUiState(isActive = true, query = "missing", hasError = true),
            selection = MemoSelectionUiState(setOf(memo.id)),
            bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
        )

        // Act
        // Normal: a search-only error keeps the content and its dialog.
        setHomeScreen(uiState = { uiState })

        // Assert
        composeRule.onNode(isDialog()).assertIsDisplayed()
    }

    private fun setHomeScreen(
        uiState: () -> HomeUiState,
        onSearchToggle: () -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
        onRequestToggleTagForSelectedMemos: () -> Unit = {},
        onToggleSelectedMemosTag: (TagId) -> Unit = {},
        onDismissBulkTagDialog: () -> Unit = {}
    ) {
        composeRule.setContent {
            TestScreenContent {
                HomeScreen(
                    uiState = uiState(),
                    onFilterSelect = {},
                    onSearchToggle = onSearchToggle,
                    onSearchQueryChange = onSearchQueryChange,
                    onMemoLongClick = {},
                    onMemoSelectionToggle = {},
                    onClearSelection = {},
                    onMoveSelectedMemosToTrash = {},
                    onSetSelectedMemosFavorite = {},
                    onRequestToggleTagForSelectedMemos = onRequestToggleTagForSelectedMemos,
                    onToggleSelectedMemosTag = onToggleSelectedMemosTag,
                    onDismissBulkTagDialog = onDismissBulkTagDialog,
                    onShareSelectedMemo = {},
                    onMemoClick = {},
                    onCreateMemoClick = {},
                    onRetry = {}
                )
            }
        }
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
