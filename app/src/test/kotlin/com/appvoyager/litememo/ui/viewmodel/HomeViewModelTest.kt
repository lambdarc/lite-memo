package com.appvoyager.litememo.ui.viewmodel

import app.cash.turbine.test
import com.appvoyager.litememo.domain.FakeMemoImageStore
import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.FakeTagRepository
import com.appvoyager.litememo.domain.MutableTimeProvider
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.memoImageFixture
import com.appvoyager.litememo.domain.model.ActiveMemoBulkWrite
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoSummary
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.SearchQuery
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.model.value.TimestampRange
import com.appvoyager.litememo.domain.repository.FakeUserSettingsRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.tagFixture
import com.appvoyager.litememo.domain.usecase.ApplyMemoBulkActionUseCase
import com.appvoyager.litememo.domain.usecase.FilterMemosUseCase
import com.appvoyager.litememo.domain.usecase.FormatMemoTextUseCase
import com.appvoyager.litememo.domain.usecase.ObserveMemosUseCase
import com.appvoyager.litememo.domain.usecase.ObserveTagsUseCase
import com.appvoyager.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.appvoyager.litememo.domain.usecase.SearchMemosUseCase
import com.appvoyager.litememo.domain.usecase.SetMemoFavoriteUseCase
import com.appvoyager.litememo.ui.state.HomeFilterUiState
import com.appvoyager.litememo.ui.state.SearchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
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
    fun uiStateReflectsObservedMemos() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(
                    id = "memo-1",
                    title = "買い物リスト",
                    tagIds = listOf(tagId),
                    updatedAt = today
                )
            ),
            tags = listOf(tagFixture(id = tagId.value, name = "生活"))
        )

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals("買い物リスト", state.memos.single().title)
    }

    @Test
    fun normalUiStateMapsThumbnailPathFromFirstImage() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(
                    id = "memo-1",
                    images = listOf(memoImageFixture(fileName = "image-1.jpg"))
                )
            )
        )

        // Act
        // Normal: home memo cards resolve the first memo image into a thumbnail path.
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals("/images/image-1.jpg", state.memos.single().thumbnailPath)
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
        val state = viewModel.uiState.first { !it.isLoading }

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
    fun normalSearchResultMapsTags() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(id = "shopping", title = "Shopping list", tagIds = listOf(tagId))
            ),
            tags = listOf(tagFixture(id = tagId.value, name = "生活"))
        )
        advanceUntilIdle()

        // Act
        // Normal: the ViewModel keeps screen-specific tag mapping for raw search results.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.search.results.isNotEmpty() }

        // Assert
        assertEquals(listOf("生活"), state.search.results.single().tags.map { it.name })
    }

    @Test
    fun normalSearchResultMapsThumbnailPath() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(
                memoFixture(
                    id = "shopping",
                    title = "Shopping list",
                    images = listOf(memoImageFixture(fileName = "search-image.jpg"))
                )
            )
        )
        advanceUntilIdle()

        // Act
        // Normal: the ViewModel keeps image path resolution for raw search results.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.search.results.isNotEmpty() }

        // Assert
        assertEquals("/images/search-image.jpg", state.search.results.single().thumbnailPath)
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
    fun setMemoFavoriteUpdatesMemoFavoriteState() = runTest(dispatcher) {
        // Arrange
        val viewModel = homeViewModel(
            memos = listOf(memoFixture(id = "memo-1", title = "Favorite"))
        )
        advanceUntilIdle()

        // Act
        viewModel.setMemoFavorite(MemoId("memo-1"), true)
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.memos.singleOrNull()?.isFavorite == true }

        // Assert
        assertTrue(state.memos.single().isFavorite)
    }

    @Test
    fun setMemoFavoriteEmitsActionErrorWhenFavoriteUpdateFails() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val viewModel = homeViewModel(
            memoRepository = SaveFailingMemoRepository(memo)
        )
        advanceUntilIdle()

        // Act & Assert
        // Flow/Error: favorite update failure emits exactly one action error event.
        viewModel.actionErrorEvent.test {
            viewModel.setMemoFavorite(MemoId("memo-1"), true)
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
        }
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
                assertEquals(
                    Unit to setOf(MemoId("memo-1")),
                    awaitItem() to state.selection.selectedMemoIds
                )
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
        val state = viewModel.uiState.first { !it.isLoading }

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
            viewModel.uiState.first { !it.isLoading }

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
        assertEquals(MemoId("memo-1") to "共有対象", selected?.id to selected?.title)
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
        viewModel.uiState.first { !it.isLoading }

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
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(emptyList<MemoId>(), state.memos.map { it.id })
    }

    @Test
    fun uiStateKeepsErrorCauseWhenObserveMemosFails() = runTest(dispatcher) {
        // Arrange
        val expected = IllegalStateException("Failed to observe memos.")
        val viewModel = homeViewModel(memoRepository = FailingMemoRepository(expected))

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.hasError }

        // Assert
        assertTrue(state.hasError)
    }

    private fun homeViewModel(
        memos: List<Memo> = emptyList(),
        tags: List<Tag> = emptyList(),
        memoRepository: MemoRepository = FakeMemoRepository(memos)
    ): HomeViewModel {
        val tagRepository = FakeTagRepository(tags)
        val userSettingsRepository = FakeUserSettingsRepository()
        return HomeViewModel(
            observeMemosUseCase = ObserveMemosUseCase(memoRepository, userSettingsRepository),
            observeTagsUseCase = ObserveTagsUseCase(tagRepository),
            filterMemosUseCase = FilterMemosUseCase(),
            searchMemosUseCase = SearchMemosUseCase(memoRepository, userSettingsRepository),
            setMemoFavoriteUseCase = SetMemoFavoriteUseCase(
                memoRepository,
                MutableTimeProvider(TimestampMillis(today + 1))
            ),
            applyMemoBulkActionUseCase = ApplyMemoBulkActionUseCase(
                memoRepository = memoRepository,
                tagRepository = tagRepository,
                currentTimeProvider = MutableTimeProvider(TimestampMillis(today + 1))
            ),
            formatMemoTextUseCase = FormatMemoTextUseCase(),
            resolveMemoImagePathUseCase = ResolveMemoImagePathUseCase(FakeMemoImageStore())
        )
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
