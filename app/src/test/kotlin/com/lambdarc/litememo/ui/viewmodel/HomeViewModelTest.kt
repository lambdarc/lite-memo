package com.lambdarc.litememo.ui.viewmodel

import app.cash.turbine.test
import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.domain.usecase.ApplyMemoBulkActionUseCase
import com.lambdarc.litememo.domain.usecase.FilterMemosUseCase
import com.lambdarc.litememo.domain.usecase.FormatMemoTextUseCase
import com.lambdarc.litememo.domain.usecase.ObserveMemosUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.lambdarc.litememo.domain.usecase.SearchMemosUseCase
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.HomeBulkTagDialogUiState
import com.lambdarc.litememo.ui.state.HomeFilterUiState
import com.lambdarc.litememo.ui.state.ScreenUiStatus
import com.lambdarc.litememo.ui.state.SearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var dispatcher: TestDispatcher
    private val today = Instant.parse("2026-05-11T12:00:00Z").toEpochMilli()

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
    fun normalUiStateMapsMemoFields() = assertMappedMemoFields(isSearch = false)

    @Test
    fun normalSearchResultsMapMemoFields() = assertMappedMemoFields(isSearch = true)

    private fun assertMappedMemoFields(isSearch: Boolean) = runTest(dispatcher) {
        // Arrange
        val tags = listOf(
            tagFixture(id = "tag-1", name = "First", color = 0xFF112233),
            tagFixture(id = "tag-2", name = "Second", color = 0xFF445566)
        )
        val memo = memoFixture(
            id = "mapped",
            title = "Mapped title",
            body = "Mapped body",
            createdAt = today,
            updatedAt = today + 1000L,
            isFavorite = true,
            tagIds = listOf(TagId("tag-2"), TagId("missing"), TagId("tag-1")),
            images = listOf(
                memoImageFixture(id = "first", fileName = "first.jpg"),
                memoImageFixture(id = "second", fileName = "second.jpg")
            )
        )
        val memoWithoutImage = memoFixture(id = "empty", title = "Mapped empty", createdAt = today)
        val viewModel = homeViewModel(memos = listOf(memo, memoWithoutImage), tags = tags)

        // Act
        // Normal: memo fields preserve tag order, skip missing tags, and use only the first image.
        if (isSearch) {
            viewModel.toggleSearch()
            viewModel.updateSearchQuery("Mapped")
        }
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            it.status != ScreenUiStatus.LOADING && (!isSearch || it.search.results.size == 2)
        }
        val results = if (isSearch) state.search.results else state.memos

        // Assert
        assertAll(
            {
                assertEquals(
                    MemoUiModel(
                        id = memo.id,
                        title = "Mapped title",
                        body = "Mapped body",
                        tags = listOf(
                            TagUiModel(TagId("tag-2"), "Second", 0xFF445566),
                            TagUiModel(TagId("tag-1"), "First", 0xFF112233)
                        ),
                        updatedAtMillis = today + 1000L,
                        isFavorite = true,
                        thumbnailPath = "/images/first.jpg"
                    ),
                    results.first { it.id == memo.id }
                )
            },
            { assertEquals(null, results.first { it.id == memoWithoutImage.id }.thumbnailPath) }
        )
    }

    @Test
    fun uiStateReflectsObservedTags() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            tags = listOf(
                tagFixture(id = "tag-1", name = "仕事"),
                tagFixture(id = "tag-2", name = "生活")
            )
        )

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.status != ScreenUiStatus.LOADING }

        // Assert
        assertEquals(listOf("仕事", "生活"), state.tags.map { it.name })
    }

    @Test
    fun selectFilterShowsOnlyFavoriteMemosWhenFilterIsFavorite() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "normal", title = "Normal"),
                memoFixture(id = "Favorite", title = "Favorite", isFavorite = true)
            )
        )
        advanceUntilIdle()

        // Act
        viewModel.selectFilter(HomeFilterUiState.Favorite)
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.selectedFilter == HomeFilterUiState.Favorite }

        // Assert
        assertEquals(listOf("Favorite"), state.memos.map { it.title })
    }

    @Test
    fun selectFilterShowsOnlyTaggedMemosWhenFilterIsByTag() = runTest(dispatcher) {
        // Arrange
        val workTagId = TagId("work")
        val lifeTagId = TagId("life")
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "work-memo", title = "Work", tagIds = listOf(workTagId)),
                memoFixture(id = "life-memo", title = "Life", tagIds = listOf(lifeTagId))
            ),
            tags = listOf(
                tagFixture(id = workTagId.value, name = "仕事"),
                tagFixture(id = lifeTagId.value, name = "生活")
            )
        )
        advanceUntilIdle()

        // Act
        viewModel.selectFilter(HomeFilterUiState.ByTag(workTagId))
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            it.selectedFilter == HomeFilterUiState.ByTag(workTagId)
        }

        // Assert
        assertEquals(listOf("Work"), state.memos.map { it.title })
    }

    @Test
    fun stateTransitionSearchQueryShowsMatchingMemosWhenSearchIsActive() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "shopping", title = "Shopping list"),
                memoFixture(id = "meeting", title = "Meeting note")
            )
        )
        advanceUntilIdle()

        // Act
        // StateTransition: query changes publish matching memos in the shared search state.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            it.search.isActive &&
                it.search.query == "shopping" &&
                it.search.results.isNotEmpty()
        }

        // Assert
        assertEquals(listOf("Shopping list"), state.search.results.map { it.title })
    }

    @Test
    fun stateTransitionSearchKeepsSelection() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("shopping")
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = memoId.value, title = "Shopping list"))
        )
        advanceUntilIdle()
        viewModel.startSelection(memoId)

        // Act
        // StateTransition: search input and results do not replace Home selection state.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.search.results.isNotEmpty() }

        // Assert
        assertEquals(setOf(memoId), state.selection.selectedMemoIds)
    }

    @Test
    fun stateTransitionToggleSearchResetsSearchWhenClosed() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()
        advanceUntilIdle()
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        viewModel.uiState.first {
            it.search.isActive && it.search.query == "shopping"
        }

        // Act
        // StateTransition: toggling an active search resets the complete search snapshot.
        viewModel.toggleSearch()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.search.isActive }

        // Assert
        assertEquals(SearchUiState(), state.search)
    }

    @Test
    fun stateTransitionCloseSearchResetsSearch() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()
        advanceUntilIdle()
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        viewModel.uiState.first {
            it.search.isActive && it.search.query == "shopping"
        }

        // Act
        // StateTransition: closing search resets active, query, error, and results together.
        viewModel.closeSearch()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.search.isActive }

        // Assert
        assertEquals(SearchUiState(), state.search)
    }

    @Test
    fun errorSearchFailureMarksSearchErrorWithoutHomeError() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memoRepository = FakeMemoRepository(
                searchResults = { flow { throw IllegalStateException("Search failed.") } }
            )
        )
        advanceUntilIdle()

        // Act
        // Error: search failure is exposed independently of the Home list error.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.search.hasError }

        // Assert
        assertAll(
            {
                assertEquals(
                    SearchUiState(isActive = true, query = "shopping", hasError = true),
                    state.search
                )
            },
            { assertEquals(ScreenUiStatus.CONTENT, state.status) }
        )
    }

    @Test
    fun stateTransitionRetryRecollectsFailedSearchWithSameQuery() = runTest(dispatcher) {
        // Arrange
        var attempts = 0
        val viewModel = homeViewModel(
            memoRepository = FakeMemoRepository(
                searchResults = {
                    flow {
                        attempts++
                        if (attempts == 1) error("Search failed.")
                        emit(listOf(memoFixture(id = "recovered")))
                    }
                }
            )
        )
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        viewModel.uiState.first { it.search.hasError }

        // Act
        // StateTransition: retry searches the same query again and replaces the search error.
        viewModel.retry()
        val state = viewModel.uiState.first { it.search.results.isNotEmpty() }

        // Assert
        assertAll(
            { assertEquals(2, attempts) },
            { assertEquals(false, state.search.hasError) },
            { assertEquals(listOf(MemoId("recovered")), state.search.results.map { it.id }) }
        )
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
        val state = viewModel.uiState.first { it.memos.singleOrNull()?.isFavorite == true }

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
        val state = viewModel.uiState.first {
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
        viewModel.uiState.first { it.bulkTagDialog.isVisible }

        // Act
        // StateTransition: starting a new selection closes the bulk tag dialog.
        viewModel.startSelection(MemoId("memo-2"))
        advanceUntilIdle()
        val state = viewModel.uiState.first {
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
            viewModel.uiState.first { it.bulkTagDialog.isVisible }

            // Act
            // StateTransition: deselecting the last memo closes the bulk tag dialog.
            viewModel.toggleMemoSelection(MemoId("memo-1"))
            advanceUntilIdle()
            val state = viewModel.uiState.first { !it.selection.isActive }

            // Assert
            assertEquals(false, state.bulkTagDialog.isVisible)
        }

    @Test
    fun toggleMemoSelectionClearsSelectionWhenLastSelectedMemoIsToggled() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.uiState.first { it.selection.isActive }

        // Act
        viewModel.toggleMemoSelection(MemoId("memo-1"))
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.selection.isActive }

        // Assert
        assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds)
    }

    @Test
    fun moveSelectedMemosToTrashClearsSelectionWhenBulkActionSucceeds() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.uiState.first { it.selection.isActive }

        // Act
        viewModel.moveSelectedMemosToTrash()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.selection.isActive && it.memos.isEmpty() }

        // Assert
        assertEquals(false, state.selection.isActive)
    }

    @Test
    fun coroutineRapidBulkTrashDoesNotEmitErrorFromReentrancy() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.uiState.first { it.selection.isActive }

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
            viewModel.uiState.first { it.selection.isActive }

            // Act & Assert
            // Flow/Error/StateTransition: bulk failure emits an error and keeps selection.
            viewModel.actionErrorEvent.test {
                viewModel.setSelectedMemosFavorite(true)
                advanceUntilIdle()
                val state = viewModel.uiState.first {
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
        val state = viewModel.uiState.first {
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
        val state = viewModel.uiState.first {
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
        val state = viewModel.uiState.first {
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
        val state = viewModel.uiState.first { it.status != ScreenUiStatus.LOADING }

        // Assert
        assertEquals(false, state.allSelectedFavorite)
    }

    @Test
    fun requestToggleTagForSelectedMemosShowsTagDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.uiState.first { it.selection.isActive }

        // Act
        viewModel.requestToggleTagForSelectedMemos()
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.bulkTagDialog.isVisible }

        // Assert
        assertEquals(true, state.bulkTagDialog.isVisible)
    }

    @Test
    fun boundaryRequestToggleTagForSelectedMemosDoesNothingWhenSelectionIsEmpty() =
        runTest(dispatcher) {
            // Arrange
            val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
            advanceUntilIdle()
            viewModel.uiState.first { it.status != ScreenUiStatus.LOADING }

            // Act
            viewModel.requestToggleTagForSelectedMemos()
            advanceUntilIdle()

            // Assert
            assertEquals(false, viewModel.uiState.value.bulkTagDialog.isVisible)
        }

    @Test
    fun dismissBulkTagDialogClearsDialog() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(memos = listOf(memoFixture(id = "memo-1")))
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.requestToggleTagForSelectedMemos()
        viewModel.uiState.first { it.bulkTagDialog.isVisible }

        // Act
        viewModel.dismissBulkTagDialog()
        advanceUntilIdle()
        val state = viewModel.uiState.first {
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
        viewModel.uiState.first { it.bulkTagDialog.isVisible }

        // Act
        viewModel.toggleSelectedMemosTag(tagId)
        val state = viewModel.uiState.first { !it.bulkTagDialog.isVisible }

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
        val state = viewModel.uiState.first {
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
        viewModel.uiState.first {
            it.selection.selectedMemoIds == setOf(MemoId("memo-1"), MemoId("memo-2"))
        }
        viewModel.requestToggleTagForSelectedMemos()

        // Act
        viewModel.toggleSelectedMemosTag(tagId)
        advanceUntilIdle()
        val state = viewModel.uiState.first {
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
        viewModel.uiState.first { it.allSelectedTagIds == setOf(tagId) }
        viewModel.requestToggleTagForSelectedMemos()

        // Act
        viewModel.toggleSelectedMemosTag(tagId)
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            !it.selection.isActive && it.memos.all { memo -> memo.tags.isEmpty() }
        }

        // Assert
        assertEquals(emptySet<String>(), state.memos.flatMap { it.tags }.map { it.id }.toSet())
    }

    @Test
    fun formatMemoTextJoinsTitleAndBodyWhenBothPresent() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()

        // Act
        val formatted = viewModel.formatMemoText(" タイトル ", " 本文 ")

        // Assert
        assertEquals("タイトル\n\n本文", formatted)
    }

    @Test
    fun formatMemoTextReturnsTitleOnlyWhenBodyIsBlank() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()

        // Act
        val formatted = viewModel.formatMemoText("タイトル", "   ")

        // Assert
        assertEquals("タイトル", formatted)
    }

    @Test
    fun formatMemoTextReturnsBodyOnlyWhenTitleIsBlank() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()

        // Act
        val formatted = viewModel.formatMemoText("   ", "本文")

        // Assert
        assertEquals("本文", formatted)
    }

    @Test
    fun formatMemoTextReturnsNullWhenTitleAndBodyAreBlank() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()

        // Act
        val formatted = viewModel.formatMemoText("   ", "")

        // Assert
        assertEquals(null, formatted)
    }

    @Test
    fun getSelectedMemoForShareReturnsMemoWhenSingleMemoIsSelected() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1", title = "共有対象"))
        )
        advanceUntilIdle()
        viewModel.startSelection(MemoId("memo-1"))
        viewModel.uiState.first { it.selection.selectedMemoIds == setOf(MemoId("memo-1")) }

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
        viewModel.uiState.first {
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
        viewModel.uiState.first { it.status != ScreenUiStatus.LOADING }

        // Act
        val selected = viewModel.getSelectedMemoForShare()

        // Assert
        assertEquals(null, selected)
    }

    @Test
    fun uiStateIsEmptyWhenNoMemosExist() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.status != ScreenUiStatus.LOADING }

        // Assert
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertEquals(emptyList<MemoId>(), state.memos.map { it.id }) }
        )
    }

    @Test
    fun uiStateKeepsErrorCauseWhenObserveMemosFails() = runTest(dispatcher) {
        // Arrange
        val expected = IllegalStateException("Failed to observe memos.")
        val viewModel = homeViewModel(memoRepository = FailingMemoRepository(expected))

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.status == ScreenUiStatus.ERROR }

        // Assert
        assertEquals(ScreenUiStatus.ERROR, state.status)
    }

    @Test
    fun errorMemoObservationFailureHidesBulkTagDialogAndKeepsSelection() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val memoRepository = FailableMemoRepository(listOf(memoFixture(id = memoId.value)))
        val viewModel = homeViewModel(memoRepository = memoRepository)
        openBulkTagDialog(viewModel, memoId)

        // Act
        // Error: a whole-screen failure does not carry the bulk tag dialog into the error state.
        memoRepository.fail()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, state.status) },
            { assertEquals(HomeBulkTagDialogUiState(), state.bulkTagDialog) },
            { assertEquals(setOf(memoId), state.selection.selectedMemoIds) }
        )
    }

    @Test
    fun errorTagObservationFailureHidesBulkTagDialog() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val tagRepository = FailableTagRepository(listOf(tagFixture(id = "tag-1")))
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = memoId.value)),
            tagRepository = tagRepository
        )
        openBulkTagDialog(viewModel, memoId)

        // Act
        // Error: a tag observation failure also hides the bulk tag dialog.
        tagRepository.fail()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, state.status) },
            { assertEquals(HomeBulkTagDialogUiState(), state.bulkTagDialog) }
        )
    }

    @Test
    fun stateTransitionRetryAfterErrorStartsWithBulkTagDialogClosed() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val memoRepository = FailableMemoRepository(listOf(memoFixture(id = memoId.value)))
        val viewModel = homeViewModel(memoRepository = memoRepository)
        openBulkTagDialog(viewModel, memoId)
        memoRepository.fail()
        runCurrent()
        memoRepository.recover()

        // Act
        // StateTransition: retry recovers the content without reopening the dialog.
        viewModel.retry()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertEquals(HomeBulkTagDialogUiState(), state.bulkTagDialog) },
            { assertEquals(setOf(memoId), state.selection.selectedMemoIds) }
        )
    }

    @Test
    fun errorSearchFailureKeepsBulkTagDialogOpen() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val memoRepository = FakeMemoRepository(
            listOf(memoFixture(id = memoId.value)),
            searchResults = { flow { throw IllegalStateException("Search failed.") } }
        )
        val viewModel = homeViewModel(memoRepository = memoRepository)
        openBulkTagDialog(viewModel, memoId)

        // Act
        // Error: a search-only failure keeps the content and its dialog.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("query")
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertTrue(state.search.hasError) },
            { assertTrue(state.bulkTagDialog.isVisible) }
        )
    }

    @Test
    fun errorUpstreamCancellationBecomesScreenError() = runTest(dispatcher) {
        // Arrange
        val repository = object : MemoRepository by FakeMemoRepository() {
            override fun observeActiveMemos(): Flow<List<Memo>> = flow {
                throw CancellationException("Upstream cancelled")
            }
        }
        val viewModel = homeViewModel(memoRepository = repository)
        backgroundScope.launch { viewModel.uiState.collect {} }

        // Act
        // Error: a cancellation raised by upstream while collection is active is a screen error.
        runCurrent()

        // Assert
        assertEquals(ScreenUiStatus.ERROR, viewModel.uiState.value.status)
    }

    @Test
    fun errorImageMappingCancellationBecomesScreenErrorWithLiveControls() = runTest(dispatcher) {
        // Arrange
        val imageStore = object : MemoImageStore by FakeMemoImageStore() {
            override fun resolveImagePath(fileName: MemoImageFileName): String =
                throw CancellationException("Image mapping cancelled")
        }
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(images = listOf(memoImageFixture()))),
            imageStore = imageStore
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        // Act
        // Error: a mapping cancellation is a screen error and controls keep publishing.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("updated")
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, state.status) },
            { assertEquals("updated", state.search.query) }
        )
    }

    @Test
    fun normalInitialStatusIsLoadingBeforeCollection() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel()

        // Act
        // Normal: screen state starts loading until observed data has been collected.
        val state = viewModel.uiState.value

        // Assert
        assertEquals(ScreenUiStatus.LOADING, state.status)
    }

    @Test
    fun stateTransitionRetryRecoversLatestControlsChangedDuringError() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val repository =
            FailableMemoRepository(listOf(memoFixture(id = memoId.value, isFavorite = true)))
        val viewModel = homeViewModel(memoRepository = repository)
        openBulkTagDialog(viewModel, memoId)
        repository.fail()
        runCurrent()
        viewModel.selectFilter(HomeFilterUiState.Favorite)
        viewModel.clearSelection()
        runCurrent()
        repository.recover()

        // Act
        // StateTransition: retry uses the current controls and restores content without its dialog.
        viewModel.retry()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertEquals(HomeFilterUiState.Favorite, state.selectedFilter) },
            { assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds) },
            { assertEquals(listOf(memoId), state.memos.map { it.id }) },
            { assertEquals(HomeBulkTagDialogUiState(), state.bulkTagDialog) }
        )
    }

    @Test
    fun stateTransitionErrorKeepsFilterQueryAndSelectionControlsLive() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val repository = FailableMemoRepository(listOf(memoFixture(id = memoId.value)))
        val viewModel = homeViewModel(memoRepository = repository)
        openBulkTagDialog(viewModel, memoId)
        repository.fail()
        runCurrent()

        // Act
        // StateTransition: controls continue publishing while observation remains failed.
        viewModel.selectFilter(HomeFilterUiState.Favorite)
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("changed during error")
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, state.status) },
            { assertEquals(HomeFilterUiState.Favorite, state.selectedFilter) },
            { assertEquals("changed during error", state.search.query) },
            { assertTrue(state.search.isActive) },
            { assertEquals(setOf(memoId), state.selection.selectedMemoIds) },
            { assertTrue(state.memos.isEmpty()) },
            { assertTrue(state.tags.isEmpty()) },
            { assertEquals(HomeBulkTagDialogUiState(), state.bulkTagDialog) }
        )
    }

    @Test
    fun stateTransitionErrorCloseSearchAndClearSelectionPublishImmediately() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val repository = FailableMemoRepository(listOf(memoFixture(id = memoId.value)))
        val viewModel = homeViewModel(memoRepository = repository)
        openBulkTagDialog(viewModel, memoId)
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("query")
        repository.fail()
        runCurrent()

        // Act
        // StateTransition: closing search and clearing selection update the failed screen.
        viewModel.closeSearch()
        viewModel.clearSelection()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, state.status) },
            { assertEquals(SearchUiState(), state.search) },
            { assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds) }
        )
    }

    @Test
    fun stateTransitionErrorToggleSelectionPublishesWhileObservationFailed() = runTest(dispatcher) {
        // Arrange
        val memoId = MemoId("memo-1")
        val repository = FailableMemoRepository(listOf(memoFixture(id = memoId.value)))
        val viewModel = homeViewModel(memoRepository = repository)
        openBulkTagDialog(viewModel, memoId)
        repository.fail()
        runCurrent()

        // Act
        // StateTransition: deselecting the last memo updates selection during a screen error.
        viewModel.toggleMemoSelection(memoId)
        runCurrent()

        // Assert
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, viewModel.uiState.value.status) },
            { assertEquals(emptySet<MemoId>(), viewModel.uiState.value.selection.selectedMemoIds) }
        )
    }

    @Test
    fun errorMappingFailureKeepsControlsLive() = runTest(dispatcher) {
        // Arrange
        val imageStore = object : MemoImageStore by FakeMemoImageStore() {
            override fun resolveImagePath(fileName: MemoImageFileName): String =
                error("Cannot resolve image")
        }
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(images = listOf(memoImageFixture()))),
            imageStore = imageStore
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        // Act
        // Error: a failed mapping does not terminate control observation.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("updated")
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, state.status) },
            { assertEquals("updated", state.search.query) }
        )
    }

    @Test
    fun stateTransitionMappingRecoversOnNextEmission() = runTest(dispatcher) {
        // Arrange
        var failMapping = true
        val imageStore = object : MemoImageStore by FakeMemoImageStore() {
            override fun resolveImagePath(fileName: MemoImageFileName): String {
                if (failMapping) error("Cannot resolve image")
                return "/images/${fileName.value}"
            }
        }
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(images = listOf(memoImageFixture()))),
            imageStore = imageStore
        )
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.toggleSearch()
        runCurrent()
        failMapping = false

        // Act
        // StateTransition: the next emission after mapping recovers restores content.
        viewModel.closeSearch()
        runCurrent()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertEquals(SearchUiState(), state.search) }
        )
    }

    private fun TestScope.openBulkTagDialog(viewModel: HomeViewModel, memoId: MemoId) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.startSelection(memoId)
        viewModel.requestToggleTagForSelectedMemos()
        runCurrent()
    }

    private fun homeViewModel(
        memos: List<Memo> = emptyList(),
        tags: List<Tag> = emptyList(),
        memoRepository: MemoRepository = FakeMemoRepository(memos),
        tagRepository: TagRepository = FakeTagRepository(tags),
        imageStore: MemoImageStore = FakeMemoImageStore()
    ): HomeViewModel {
        val displaySettingsRepository = FakeDisplaySettingsRepository()
        return HomeViewModel(
            observeMemosUseCase = ObserveMemosUseCase(memoRepository, displaySettingsRepository),
            observeTagsUseCase = ObserveTagsUseCase(tagRepository),
            filterMemosUseCase = FilterMemosUseCase(),
            searchMemosUseCase = SearchMemosUseCase(memoRepository, displaySettingsRepository),
            applyMemoBulkActionUseCase = ApplyMemoBulkActionUseCase(
                memoRepository = memoRepository,
                tagRepository = tagRepository,
                currentTimeProvider = MutableTimeProvider(TimestampMillis(today + 1))
            ),
            formatMemoTextUseCase = FormatMemoTextUseCase(),
            resolveMemoImagePathUseCase = ResolveMemoImagePathUseCase(imageStore)
        )
    }

    private class FailableMemoRepository(
        memos: List<Memo>,
        private val delegate: FakeMemoRepository = FakeMemoRepository(memos)
    ) : MemoRepository by delegate {
        private val failure = MutableStateFlow<Throwable?>(null)

        fun fail() {
            failure.value = IllegalStateException("Failed to observe memos.")
        }

        fun recover() {
            failure.value = null
        }

        override fun observeActiveMemos(): Flow<List<Memo>> =
            combine(delegate.observeActiveMemos(), failure) { memos, error ->
                if (error != null) throw error
                memos
            }
    }

    private class FailableTagRepository(
        tags: List<Tag>,
        private val delegate: FakeTagRepository = FakeTagRepository(tags)
    ) : TagRepository by delegate {
        private val failure = MutableStateFlow<Throwable?>(null)

        fun fail() {
            failure.value = IllegalStateException("Failed to observe tags.")
        }

        override fun observeTags(): Flow<List<Tag>> =
            combine(delegate.observeTags(), failure) { tags, error ->
                if (error != null) throw error
                tags
            }
    }

    private class FailingMemoRepository(private val throwable: Throwable) :
        MemoRepository by FakeMemoRepository() {

        override fun observeActiveMemos(): Flow<List<Memo>> = flow {
            throw throwable
        }

        override fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummary>> = flow {
            throw throwable
        }

        override fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>> = flow {
            throw throwable
        }

        override fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>> =
            flow { throw throwable }

    }

    private class SaveFailingMemoRepository(memo: Memo) :
        MemoRepository by FakeMemoRepository(listOf(memo)) {

        override suspend fun saveMemo(memo: Memo): Unit = error("Failed to save memo.")

        override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>): Unit =
            error("Failed to save active memos.")
    }
}
