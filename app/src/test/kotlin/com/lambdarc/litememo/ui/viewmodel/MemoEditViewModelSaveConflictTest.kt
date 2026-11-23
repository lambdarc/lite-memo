package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.ui.state.MemoEditUiResult
import com.lambdarc.litememo.ui.state.ScreenUiStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import kotlin.time.Duration.Companion.milliseconds

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

    @Test
    fun coroutineAutosaveCancelledAfterCommitStillUpdatesBaseline() = runTest(dispatcher) {
        // Arrange
        val repository = CommitThenSuspendMemoRepository(FakeMemoRepository(listOf(original)))
        val viewModel = memoEditViewModel(memo = original, memoRepository = repository)
        advanceUntilIdle()
        viewModel.updateTitle("First")
        advanceTimeBy(1_001.milliseconds)
        runCurrent()

        // Act
        // Coroutine: typing during a committed save does not make the next save conflict with it
        viewModel.updateTitle("Second")
        repository.releaseReturn.complete(Unit)
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(false, viewModel.uiState.value.hasSaveConflict) },
            { assertEquals("Second", repository.delegate.currentMemos().single().title.value) }
        )
    }

    @Test
    fun interactionImageCopiedDuringSaveConflictIsDiscarded() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(original))
        val imageStore = BlockingMemoImageStore()
        val viewModel = memoEditViewModel(
            memo = original,
            memoRepository = repository,
            memoImageStore = imageStore
        )
        advanceUntilIdle()
        repository.replaceExternally(imported)
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()
        viewModel.updateTitle("Edited")
        advanceUntilIdle()

        // Act
        // Interaction: a copy finishing while the draft is frozen is removed instead of leaking
        imageStore.releaseSave.complete(Unit)
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(listOf(MemoImageFileName("copied.jpg")), imageStore.deletedFileNames) },
            { assertTrue(viewModel.uiState.value.images.none { it.fileName == "copied.jpg" }) }
        )
    }

    @Test
    fun interactionImageCopiedAcrossConflictReloadIsNotAddedToReloadedMemo() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(original))
        val imageStore = BlockingMemoImageStore()
        val viewModel = memoEditViewModel(
            memo = original,
            memoRepository = repository,
            memoImageStore = imageStore
        )
        advanceUntilIdle()
        repository.replaceExternally(imported)
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()
        viewModel.updateTitle("Edited")
        advanceUntilIdle()
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()

        // Act
        // Interaction: a copy started for the discarded draft never joins the reloaded memo
        imageStore.releaseSave.complete(Unit)
        advanceUntilIdle()

        // Assert
        assertAll(
            {
                assertEquals(
                    listOf("after-import.jpg"),
                    viewModel.uiState.value.images.map { it.fileName }
                )
            },
            {
                assertEquals(
                    listOf(MemoImageFileName("copied.jpg")),
                    imageStore.deletedFileNames
                )
            }
        )
    }

    @Test
    fun errorBlankDraftOfExternallyUpdatedMemoRaisesConflictInsteadOfTrashing() =
        runTest(dispatcher) {
            // Arrange
            val textOnly = memoFixture(id = "memo-1", title = "Original")
            val repository = FakeMemoRepository(listOf(textOnly))
            val viewModel = memoEditViewModel(memo = textOnly, memoRepository = repository)
            advanceUntilIdle()
            repository.replaceExternally(textOnly.copy(title = MemoTitle("Imported")))
            viewModel.updateTitle("")
            viewModel.updateBody("")

            // Act
            // Error: an emptied draft cannot trash a memo it has not seen in its latest form
            viewModel.finishEditing()
            advanceUntilIdle()

            // Assert
            assertAll(
                { assertTrue(viewModel.uiState.value.hasSaveConflict) },
                { assertEquals(null, repository.currentMemos().single().deletedAt) }
            )
        }

    @Test
    fun errorFlushAfterReloadFindsMemoTrashedKeepsDiscardedDraftOut() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(original))
        val viewModel = conflictedViewModel(repository)
        repository.moveMemoToTrash(original.id, TimestampMillis(3_000L))
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()

        // Act
        // Error: a memo trashed elsewhere is not rewritten from the discarded draft
        viewModel.flushEdits()
        advanceUntilIdle()

        // Assert
        val stored = repository.currentMemos().single()
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, viewModel.uiState.value.status) },
            { assertEquals("Imported", stored.title.value) },
            { assertEquals(TimestampMillis(3_000L), stored.deletedAt) }
        )
    }

    @Test
    fun flowNewMemoSessionLeavesTrashedMemoAfterReloadFindsNothing() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.updateTitle("Draft")
        advanceUntilIdle()
        val generatedId = repository.currentMemos().single().id
        repository.moveMemoToTrash(generatedId, TimestampMillis(3_000L))
        viewModel.updateTitle("Edited")
        advanceUntilIdle()
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()

        // Act
        // Flow: leaving neither saves nor discards a memo trashed elsewhere
        viewModel.finishEditing()
        advanceUntilIdle()

        // Assert
        val stored = repository.currentMemos().single()
        assertAll(
            {
                assertTrue(
                    viewModel.uiResults.value.single() is MemoEditUiResult.NavigateBack
                )
            },
            { assertEquals("Draft", stored.title.value) },
            { assertEquals(TimestampMillis(3_000L), stored.deletedAt) }
        )
    }

    @Test
    fun flowNewMemoSessionRetryAfterReloadFindsNothingKeepsTrashedMemo() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.updateTitle("Draft")
        advanceUntilIdle()
        val generatedId = repository.currentMemos().single().id
        repository.moveMemoToTrash(generatedId, TimestampMillis(3_000L))
        viewModel.updateTitle("Edited")
        advanceUntilIdle()
        viewModel.reloadAfterSaveConflict()
        advanceUntilIdle()
        viewModel.reload()
        advanceUntilIdle()

        // Act
        // Flow: retrying looks up the saved memo instead of starting an empty draft to discard
        viewModel.finishEditing()
        advanceUntilIdle()

        // Assert
        val stored = repository.currentMemos().single()
        assertAll(
            { assertEquals(ScreenUiStatus.ERROR, viewModel.uiState.value.status) },
            { assertEquals("Draft", stored.title.value) },
            { assertEquals(TimestampMillis(3_000L), stored.deletedAt) }
        )
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

    private class CommitThenSuspendMemoRepository(val delegate: FakeMemoRepository) :
        MemoRepository by delegate {

        val releaseReturn = CompletableDeferred<Unit>()

        override suspend fun saveMemo(memo: Memo, expectedMemo: Memo?) {
            delegate.saveMemo(memo, expectedMemo)
            releaseReturn.await()
        }
    }

    private class BlockingMemoImageStore : MemoImageStore {

        val releaseSave = CompletableDeferred<Unit>()
        val deletedFileNames = mutableListOf<MemoImageFileName>()

        override suspend fun saveImage(source: ImageSourceReference): MemoImage {
            releaseSave.await()
            return memoImageFixture(id = "copied", fileName = "copied.jpg")
        }

        override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
            deletedFileNames += fileNames
        }

        override fun resolveImagePath(fileName: MemoImageFileName): String =
            "/images/${fileName.value}"
    }

}
