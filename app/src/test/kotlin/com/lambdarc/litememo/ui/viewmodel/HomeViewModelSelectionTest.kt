package com.lambdarc.litememo.ui.viewmodel

import app.cash.turbine.test
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.HomeFilterUiState
import com.lambdarc.litememo.ui.state.HomeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSelectionTest {

    private lateinit var dispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stateTransitionSetSelectedMemosFavoriteMarksMemoAsFavorite() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1", title = "Favorite"))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))

        // Act
        // StateTransition: a bulk favorite action is reflected in the memo list state
        viewModel.setSelectedMemosFavorite(true)
        advanceUntilIdle()
        val state = viewModel.firstContent { it.memos.singleOrNull()?.isFavorite == true }

        // Assert
        assertTrue(state.memos.single().isFavorite)
    }

    @Test
    fun startSelectionSelectsMemoWhenMemoIsLongPressed() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()

        // Act
        viewModel.startSelection(MemoId("memo-1"))
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"))
        }

        // Assert
        assertEquals(setOf(MemoId("memo-1")), state.selection.selectedMemoIds)
    }

    @Test
    fun stateTransitionStartSelectionClosesBulkTagDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1"), memoFixture(id = "memo-2"))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.requestToggleTagForSelectedMemos()
        viewModel.firstContent { it.bulkTagDialog.isVisible }

        // Act
        // StateTransition: starting a new selection closes the bulk tag dialog.
        viewModel.startSelection(MemoId("memo-2"))
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-2"))
        }

        // Assert
        assertEquals(false, state.bulkTagDialog.isVisible)
    }

    @Test
    fun stateTransitionToggleMemoSelectionClosesBulkTagDialogWhenSelectionBecomesEmpty() =
        runTest(dispatcher) {
            // Arrange
            val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
            advanceUntilIdle()
            viewModel.startSelection(MemoId("memo-1"))
            viewModel.requestToggleTagForSelectedMemos()
            viewModel.firstContent { it.bulkTagDialog.isVisible }

            // Act
            // StateTransition: deselecting the last memo closes the bulk tag dialog.
            viewModel.toggleMemoSelection(MemoId("memo-1"))
            advanceUntilIdle()
            val state = viewModel.firstContent { !it.selection.isActive }

            // Assert
            assertEquals(false, state.bulkTagDialog.isVisible)
        }

    @Test
    fun toggleMemoSelectionClearsSelectionWhenLastSelectedMemoIsToggled() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.firstContent { it.selection.isActive }

        // Act
        viewModel.toggleMemoSelection(MemoId("memo-1"))
        advanceUntilIdle()
        val state = viewModel.firstContent { !it.selection.isActive }

        // Assert
        assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds)
    }

    @Test
    fun moveSelectedMemosToTrashClearsSelectionWhenBulkActionSucceeds() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.firstContent { it.selection.isActive }

        // Act
        viewModel.moveSelectedMemosToTrash()
        advanceUntilIdle()
        val state = viewModel.firstContent { !it.selection.isActive && it.memos.isEmpty() }

        // Assert
        assertEquals(false, state.selection.isActive)
    }

    @Test
    fun coroutineRapidBulkTrashDoesNotEmitErrorFromReentrancy() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.firstContent { it.selection.isActive }

        // Act & Assert
        // Coroutine/Boundary: the in-flight guard blocks the second rapid bulk action so the
        // already-trashed memo is not re-processed (which would emit a spurious error).
        viewModel.actionErrorEvent.test {
            viewModel.moveSelectedMemosToTrash()
            viewModel.moveSelectedMemosToTrash()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun setSelectedMemosFavoriteKeepsSelectionAndEmitsActionErrorWhenBulkActionFails() =
        runTest(dispatcher) {
            // Arrange
            val memo = memoFixture(id = "memo-1")
            val viewModel = homeViewModel(
                memoRepository = SaveFailingMemoRepository(memo)
            )
            advanceUntilIdle()
            viewModel.startSelection(MemoId("memo-1"))
            viewModel.firstContent { it.selection.isActive }

            // Act & Assert
            // Flow/Error/StateTransition: bulk failure emits an error and keeps selection.
            viewModel.actionErrorEvent.test {
                viewModel.setSelectedMemosFavorite(true)
                advanceUntilIdle()
                val state = viewModel.firstContent {
                    it.selection.selectedMemoIds == setOf(MemoId("memo-1"))
                }
                awaitItem()
                assertEquals(setOf(MemoId("memo-1")), state.selection.selectedMemoIds)
            }
        }

    @Test
    fun uiStateMarksAllSelectedFavoriteWhenEverySelectedMemoIsFavorite() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "memo-1", isFavorite = true),
                memoFixture(id = "memo-2", isFavorite = true)
            )
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))

        // Act
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"), MemoId("memo-2"))
        }

        // Assert
        assertTrue(state.allSelectedFavorite)
    }

    @Test
    fun uiStateDoesNotMarkAllSelectedFavoriteWhenSelectionIsMixed() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "memo-1", isFavorite = true),
                memoFixture(id = "memo-2", isFavorite = false)
            )
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))

        // Act
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"), MemoId("memo-2"))
        }

        // Assert
        assertEquals(false, state.allSelectedFavorite)
    }

    @Test
    fun uiStateKeepsMixedFavoriteSelectionWhenNonFavoriteMemoIsFilteredOut() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "favorite", isFavorite = true),
                memoFixture(id = "normal", isFavorite = false)
            )
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("favorite"))
        viewModel.toggleMemoSelection(MemoId("normal"))

        // Act
        viewModel.selectFilter(HomeFilterUiState.Favorite)
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selectedFilter == HomeFilterUiState.Favorite &&
                it.selection.selectedMemoIds == setOf(MemoId("favorite"), MemoId("normal"))
        }

        // Assert
        assertEquals(false, state.allSelectedFavorite)
    }

    @Test
    fun uiStateDoesNotMarkAllSelectedFavoriteWhenNoMemoIsSelected() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1", isFavorite = true))
        )

        // Act
        advanceUntilIdle()
        val state = viewModel.firstContent()

        // Assert
        assertEquals(false, state.allSelectedFavorite)
    }

    @Test
    fun requestToggleTagForSelectedMemosShowsTagDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.firstContent { it.selection.isActive }

        // Act
        viewModel.requestToggleTagForSelectedMemos()
        advanceUntilIdle()
        val state = viewModel.firstContent { it.bulkTagDialog.isVisible }

        // Assert
        assertEquals(true, state.bulkTagDialog.isVisible)
    }

    @Test
    fun boundaryRequestToggleTagForSelectedMemosDoesNothingWhenSelectionIsEmpty() =
        runTest(dispatcher) {
            // Arrange
            val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
            advanceUntilIdle()
            viewModel.uiState.first { it !is HomeUiState.Loading }

            // Act
            viewModel.requestToggleTagForSelectedMemos()
            advanceUntilIdle()

            // Assert
            assertEquals(false, viewModel.uiState.value.asContent().bulkTagDialog.isVisible)
        }

    @Test
    fun dismissBulkTagDialogClearsDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.requestToggleTagForSelectedMemos()
        viewModel.firstContent { it.bulkTagDialog.isVisible }

        // Act
        viewModel.dismissBulkTagDialog()
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selection.isActive && !it.bulkTagDialog.isVisible
        }

        // Assert
        assertEquals(false, state.bulkTagDialog.isVisible)
    }

    @Test
    fun toggleSelectedMemosTagClosesTagDialogImmediately() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1")),
            tags = listOf(tagFixture(id = tagId.value))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.requestToggleTagForSelectedMemos()
        viewModel.firstContent { it.bulkTagDialog.isVisible }

        // Act
        viewModel.toggleSelectedMemosTag(tagId)
        val state = viewModel.firstContent { !it.bulkTagDialog.isVisible }

        // Assert
        assertEquals(false, state.bulkTagDialog.isVisible)
    }

    @Test
    fun uiStateMarksTagSelectedWhenEverySelectedMemoHasTag() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "memo-1", tagIds = listOf(tagId)),
                memoFixture(id = "memo-2", tagIds = listOf(tagId))
            ),
            tags = listOf(tagFixture(id = tagId.value))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))

        // Act
        advanceUntilIdle()
        val state = viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"), MemoId("memo-2"))
        }

        // Assert
        assertEquals(setOf(tagId), state.allSelectedTagIds)
    }

    @Test
    fun toggleSelectedMemosTagAddsTagWhenSomeSelectedMemosDoNotHaveTag() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "memo-1", tagIds = listOf(tagId)),
                memoFixture(id = "memo-2")
            ),
            tags = listOf(tagFixture(id = tagId.value))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))
        viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"), MemoId("memo-2"))
        }
        viewModel.requestToggleTagForSelectedMemos()

        // Act
        viewModel.toggleSelectedMemosTag(tagId)
        advanceUntilIdle()
        val state = viewModel.firstContent {
            !it.selection.isActive &&
                it.memos.all { memo -> memo.tags.any { tag -> tag.id == tagId } }
        }

        // Assert
        assertEquals(setOf(MemoId("memo-1"), MemoId("memo-2")), state.memos.map { it.id }.toSet())
    }

    @Test
    fun toggleSelectedMemosTagRemovesTagWhenEverySelectedMemoHasTag() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "memo-1", tagIds = listOf(tagId)),
                memoFixture(id = "memo-2", tagIds = listOf(tagId))
            ),
            tags = listOf(tagFixture(id = tagId.value))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))
        viewModel.firstContent { it.allSelectedTagIds == setOf(tagId) }
        viewModel.requestToggleTagForSelectedMemos()

        // Act
        viewModel.toggleSelectedMemosTag(tagId)
        advanceUntilIdle()
        val state = viewModel.firstContent {
            !it.selection.isActive && it.memos.all { memo -> memo.tags.isEmpty() }
        }

        // Assert
        assertEquals(emptySet<String>(), state.memos.flatMap { it.tags }.map { it.id }.toSet())
    }

    @Test
    fun getSelectedMemoForShareReturnsMemoWhenSingleMemoIsSelected() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1", title = "共有対象"))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.firstContent { it.selection.selectedMemoIds == setOf(MemoId("memo-1")) }

        // Act
        val selected = viewModel.getSelectedMemoForShare()

        // Assert
        assertAll(
            { assertEquals(MemoId("memo-1"), selected?.id) },
            { assertEquals("共有対象", selected?.title) }
        )
    }

    @Test
    fun getSelectedMemoForShareReturnsNullWhenMultipleMemosAreSelected() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "memo-1"),
                memoFixture(id = "memo-2")
            )
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))
        viewModel.firstContent {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"), MemoId("memo-2"))
        }

        // Act
        val selected = viewModel.getSelectedMemoForShare()

        // Assert
        assertEquals(null, selected)
    }

    @Test
    fun getSelectedMemoForShareReturnsNullWhenNoMemoIsSelected() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1"))
        )
        advanceUntilIdle()
        viewModel.uiState.first { it !is HomeUiState.Loading }

        // Act
        val selected = viewModel.getSelectedMemoForShare()

        // Assert
        assertEquals(null, selected)
    }

    @Test
    fun boundaryToggleSelectedMemosTagDoesNothingWhenBulkTagDialogIsHidden() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val memoId = MemoId("memo-1")
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = memoId.value)),
            tags = listOf(tagFixture(id = tagId.value))
        )
        collectUiState(viewModel)
        viewModel.startSelection(memoId)
        runCurrent()

        // Act
        // Boundary: a tag toggle without a visible dialog is ignored.
        viewModel.toggleSelectedMemosTag(tagId)
        runCurrent()

        // Assert
        val state = viewModel.uiState.value.asContent()
        assertAll(
            { assertEquals(setOf(memoId), state.selection.selectedMemoIds) },
            { assertEquals(emptyList<TagUiModel>(), state.memos.single().tags) }
        )
    }

    @Test
    fun boundaryToggleSelectedMemosTagUsesAddWhenUiStateIsError() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val memoId = MemoId("memo-1")
        val memoRepository = FailableMemoRepository(
            listOf(memoFixture(id = memoId.value, tagIds = listOf(tagId)))
        )
        val viewModel = homeViewModel(
            memoRepository = memoRepository,
            tags = listOf(tagFixture(id = tagId.value))
        )
        openBulkTagDialog(viewModel, memoId)
        memoRepository.fail()
        runCurrent()

        // Act
        // Boundary: without content state the selected tags are unknown, so the tag is added.
        viewModel.toggleSelectedMemosTag(tagId)
        runCurrent()
        memoRepository.recover()
        viewModel.retry()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value.asContent()
        assertEquals(listOf(tagId), state.memos.single().tags.map { it.id })
    }

    @Test
    fun boundaryToggleMemoSelectionKeepsBulkTagDialogWhenSelectionRemains() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1"), memoFixture(id = "memo-2"))
        )
        collectUiState(viewModel)
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.toggleMemoSelection(MemoId("memo-2"))
        viewModel.requestToggleTagForSelectedMemos()
        runCurrent()

        // Act
        // Boundary: the dialog stays open while at least one memo remains selected.
        viewModel.toggleMemoSelection(MemoId("memo-2"))
        runCurrent()

        // Assert
        val state = viewModel.uiState.value.asContent()
        assertAll(
            { assertEquals(setOf(MemoId("memo-1")), state.selection.selectedMemoIds) },
            { assertTrue(state.bulkTagDialog.isVisible) }
        )
    }

    @Test
    fun stateTransitionClearSelectionClearsSelectionAndClosesBulkTagDialog() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = memoId.value)))
        openBulkTagDialog(viewModel, memoId)

        // Act
        // StateTransition: clearing selection also closes the bulk tag dialog.
        viewModel.clearSelection()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value.asContent()
        assertAll(
            { assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds) },
            { assertFalse(state.bulkTagDialog.isVisible) }
        )
    }

    @Test
    fun boundaryMoveSelectedMemosToTrashDoesNothingWhenSelectionIsEmpty() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        collectUiState(viewModel)
        runCurrent()

        // Act & Assert
        // Boundary: a bulk action without selection neither changes memos nor emits an error.
        viewModel.actionErrorEvent.test {
            viewModel.moveSelectedMemosToTrash()
            runCurrent()
            expectNoEvents()
            assertEquals(1, viewModel.uiState.value.asContent().memos.size)
        }
    }

    @Test
    fun errorToggleSelectedMemosTagEmitsActionErrorAndClosesBulkTagDialogWhenBulkActionFails() =
        runTest(dispatcher) {
            // Arrange
            val tagId = TagId("tag-1")
            val memoId = MemoId("memo-1")
            val viewModel = homeViewModel(
                tags = listOf(tagFixture(id = tagId.value)),
                memoRepository = SaveFailingMemoRepository(memoFixture(id = memoId.value))
            )
            openBulkTagDialog(viewModel, memoId)

            // Act & Assert
            // Error: a failed bulk tag action emits an error and leaves the dialog closed.
            viewModel.actionErrorEvent.test {
                viewModel.toggleSelectedMemosTag(tagId)
                runCurrent()
                awaitItem()
                val state = viewModel.uiState.value.asContent()
                assertAll(
                    { assertEquals(setOf(memoId), state.selection.selectedMemoIds) },
                    { assertFalse(state.bulkTagDialog.isVisible) }
                )
            }
        }

    @Test
    fun boundaryAllSelectedTagIdsIsEmptyWhenSelectedMemoDoesNotExist() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1", tagIds = listOf(tagId))),
            tags = listOf(tagFixture(id = tagId.value))
        )
        collectUiState(viewModel)
        viewModel.startSelection(MemoId("memo-1"))

        // Act
        // Boundary: a selected id absent from the memo list yields no common tags.
        viewModel.toggleMemoSelection(MemoId("missing"))
        runCurrent()

        // Assert
        val state = viewModel.uiState.value.asContent()
        assertEquals(emptySet<TagId>(), state.allSelectedTagIds)
    }

    @Test
    fun boundaryUiStateDoesNotMarkAllSelectedFavoriteWhenSelectedMemoDoesNotExist() =
        runTest(dispatcher) {
            // Arrange
            val viewModel = homeViewModel(
                memos = listOf(memoFixture(id = "memo-1", isFavorite = true))
            )
            collectUiState(viewModel)
            viewModel.startSelection(MemoId("memo-1"))

            // Act
            // Boundary: a selected id absent from the memo list is not a favorite.
            viewModel.toggleMemoSelection(MemoId("missing"))
            runCurrent()

            // Assert
            val state = viewModel.uiState.value.asContent()
            assertFalse(state.allSelectedFavorite)
        }

    @Test
    fun normalGetSelectedMemoForShareReturnsMemoFromSearchResultsWhenFilteredOutOfList() =
        runTest(dispatcher) {
            // Arrange
            val tagId = TagId("tag-1")
            val viewModel = homeViewModel(
                memos = listOf(
                    memoFixture(id = "work-memo", title = "Work"),
                    memoFixture(id = "tagged-memo", title = "Tagged", tagIds = listOf(tagId))
                ),
                tags = listOf(tagFixture(id = tagId.value))
            )
            collectUiState(viewModel)
            viewModel.selectFilter(HomeFilterUiState.ByTag(tagId))
            viewModel.toggleSearch()
            viewModel.updateSearchQuery("Work")
            viewModel.startSelection(MemoId("work-memo"))
            advanceUntilIdle()

            // Act
            // Normal: a selected memo hidden by the filter is still found in search results.
            val selected = viewModel.getSelectedMemoForShare()

            // Assert
            assertEquals(MemoId("work-memo"), selected?.id)
        }

    @Test
    fun boundaryGetSelectedMemoForShareReturnsNullWhenSelectedMemoIsNotVisible() =
        runTest(dispatcher) {
            // Arrange
            val tagId = TagId("tag-1")
            val viewModel = homeViewModel(
                memos = listOf(memoFixture(id = "work-memo", title = "Work")),
                tags = listOf(tagFixture(id = tagId.value))
            )
            collectUiState(viewModel)
            viewModel.selectFilter(HomeFilterUiState.ByTag(tagId))
            viewModel.startSelection(MemoId("work-memo"))
            advanceUntilIdle()

            // Act
            // Boundary: a selected memo hidden by the filter and search cannot be shared.
            val selected = viewModel.getSelectedMemoForShare()

            // Assert
            assertEquals(null, selected)
        }

    @Test
    fun boundaryGetSelectedMemoForShareReturnsNullWhenUiStateIsError() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val memoRepository = FailableMemoRepository(listOf(memoFixture(id = memoId.value)))
        val viewModel = homeViewModel(memoRepository = memoRepository)
        openBulkTagDialog(viewModel, memoId)
        memoRepository.fail()
        runCurrent()

        // Act
        // Boundary: no memo can be shared while the screen is in the error state.
        val selected = viewModel.getSelectedMemoForShare()

        // Assert
        assertEquals(null, selected)
    }
}
