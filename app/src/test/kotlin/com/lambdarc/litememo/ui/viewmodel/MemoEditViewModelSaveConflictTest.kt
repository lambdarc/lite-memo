package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.ui.state.MemoEditUiResult
import com.lambdarc.litememo.ui.state.ScreenUiStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoEditViewModelSaveConflictTest {

    private lateinit var dispatcher: TestDispatcher

    private val original = memoFixture(
        id = "memo-1",
        title = "Original",
        images = listOf(memoImageFixture(id = "image-a", fileName = "before-import.jpg"))
    )
    private val imported = original.copy(
        title = MemoTitle("Imported"),
        images = listOf(memoImageFixture(id = "image-a", fileName = "after-import.jpg"))
    )

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
    fun stateTransitionAutosaveAfterExternalUpdateRaisesConflictWithoutOverwriting() =
        runTest(dispatcher) {
            // Arrange
            val repository = FakeMemoRepository(listOf(original))
            val viewModel = memoEditViewModel(memo = original, memoRepository = repository)
            advanceUntilIdle()
            repository.replaceExternally(imported)

            // Act
            // StateTransition: a stale draft is held back instead of overwriting the import
            viewModel.updateTitle("Edited")
            advanceUntilIdle()

            // Assert
            assertAll(
                { assertTrue(viewModel.uiState.value.hasSaveConflict) },
                { assertEquals(listOf(imported), repository.currentMemos()) }
            )
        }

    @Test
    fun boundaryEditsAreIgnoredWhileSaveConflictIsPending() = runTest(dispatcher) {
        // Arrange
        val viewModel = conflictedViewModel(FakeMemoRepository(listOf(original)))

        // Act
        // Boundary: the draft is frozen until the latest memo is reloaded
        viewModel.updateTitle("Ignored")

        // Assert
        assertEquals("Edited", viewModel.uiState.value.title)
    }

    @Test
    fun flowFinishEditingDuringSaveConflictStaysWithoutResults() = runTest(dispatcher) {
        // Arrange
        val viewModel = conflictedViewModel(FakeMemoRepository(listOf(original)))

        // Act
        // Flow: leaving is blocked without reporting a generic save failure
        viewModel.finishEditing()
        advanceUntilIdle()

        // Assert
        assertEquals(emptyList<MemoEditUiResult>(), viewModel.uiResults.value)
    }

    @Test
    fun stateTransitionReloadAfterSaveConflictShowsLatestMemo() = runTest(dispatcher) {
        // Arrange
        val viewModel = conflictedViewModel(FakeMemoRepository(listOf(original)))

        // Act
        // StateTransition: reloading replaces the stale draft with the stored memo
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertEquals(false, state.hasSaveConflict) },
            { assertEquals("Imported", state.title) },
            { assertEquals(listOf("after-import.jpg"), state.images.map { it.fileName }) }
        )
    }

    @Test
    fun normalEditAfterConflictReloadIsSaved() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(original))
        val viewModel = conflictedViewModel(repository)
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()

        // Act
        // Normal: the reloaded memo becomes the new baseline for later saves
        viewModel.updateTitle("Edited again")
        advanceUntilIdle()

        // Assert
        assertEquals("Edited again", repository.currentMemos().single().title.value)
    }

    @Test
    fun normalConsecutiveAutosavesOfExistingMemoDoNotConflict() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(original))
        val viewModel = memoEditViewModel(memo = original, memoRepository = repository)
        advanceUntilIdle()
        viewModel.updateTitle("First")
        advanceUntilIdle()

        // Act
        // Normal: the session's own save becomes the baseline for the next save
        viewModel.updateTitle("Second")
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(false, viewModel.uiState.value.hasSaveConflict) },
            { assertEquals("Second", repository.currentMemos().single().title.value) }
        )
    }

    @Test
    fun interactionReloadAfterSaveConflictDeletesUnsavedImageCopies() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(original))
        val imageStore = FakeMemoImageStore()
        val viewModel = memoEditViewModel(
            memo = original,
            memoRepository = repository,
            memoImageStore = imageStore
        )
        advanceUntilIdle()
        repository.replaceExternally(imported)
        viewModel.attachImages(listOf("content://images/1"))
        advanceUntilIdle()

        // Act
        // Interaction: copies that never reached the database are discarded with the draft
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(MemoImageFileName("image-1.jpg")), imageStore.deletedFileNames)
    }

    private suspend fun TestScope.conflictedViewModel(
        repository: FakeMemoRepository
    ): MemoEditViewModel {
        val viewModel = memoEditViewModel(memo = original, memoRepository = repository)
        advanceUntilIdle()
        repository.replaceExternally(imported)
        viewModel.updateTitle("Edited")
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun FakeMemoRepository.replaceExternally(memo: Memo) {
        saveMemo(memo, expectedMemo = getActiveMemo(memo.id))
    }

}
