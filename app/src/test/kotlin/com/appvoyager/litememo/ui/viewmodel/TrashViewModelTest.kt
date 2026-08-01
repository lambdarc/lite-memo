package com.appvoyager.litememo.ui.viewmodel

import app.cash.turbine.test
import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.FakeTagRepository
import com.appvoyager.litememo.domain.MutableTimeProvider
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.tagFixture
import com.appvoyager.litememo.domain.usecase.DeleteMemosPermanentlyUseCase
import com.appvoyager.litememo.domain.usecase.ObserveTagsUseCase
import com.appvoyager.litememo.domain.usecase.ObserveTrashedMemosUseCase
import com.appvoyager.litememo.domain.usecase.PurgeExpiredTrashedMemosUseCase
import com.appvoyager.litememo.domain.usecase.RestoreMemosFromTrashUseCase
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
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

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
    fun uiStateShowsTrashedMemos() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", title = "Trash", deletedAt = 2_000L)
        val viewModel = trashViewModel(memoRepository = FakeMemoRepository(listOf(memo)))

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(listOf("Trash"), state.memos.map { it.title })
    }

    @Test
    fun startSelectionSelectsMemo() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val viewModel = trashViewModel(memoRepository = FakeMemoRepository(listOf(memo)))
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }

        // Act
        viewModel.startSelection(memo.id)
        val state = viewModel.uiState.first { it.selection.isActive }

        // Assert
        assertEquals(setOf(memo.id), state.selection.selectedMemoIds)
    }

    @Test
    fun stateTransitionStartSelectionClosesEmptyTrashDialog() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val viewModel = trashViewModel(memoRepository = FakeMemoRepository(listOf(memo)))
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }
        viewModel.requestEmptyTrash()
        viewModel.uiState.first { it.showEmptyTrashDialog }

        // Act
        // StateTransition: starting a selection closes the empty trash dialog.
        viewModel.startSelection(memo.id)
        val state = viewModel.uiState.first { it.selection.isActive }

        // Assert
        assertEquals(false, state.showEmptyTrashDialog)
    }

    @Test
    fun stateTransitionUiStateDropsSelectedMemoIdWhenMemoLeavesTrash() = runTest(dispatcher) {
        // Arrange
        val memo1 = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val memo2 = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(memo1, memo2))
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.size == 2 }
        viewModel.startSelection(memo1.id)
        viewModel.toggleMemoSelection(memo2.id)
        viewModel.uiState.first { it.selection.selectedMemoIds == setOf(memo1.id, memo2.id) }

        // Act
        // StateTransition: a memo that is no longer visible leaves the selection.
        repository.deleteMemosPermanently(listOf(memo1.id))
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.memos.size == 1 }

        // Assert
        assertEquals(setOf(memo2.id), state.selection.selectedMemoIds)
    }

    @Test
    fun boundaryUiStateClearsSelectionWhenTrashBecomesEmpty() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(memo))
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.size == 1 }
        viewModel.startSelection(memo.id)
        viewModel.uiState.first { it.selection.isActive }

        // Act
        // Boundary: an empty trash leaves nothing selected.
        repository.deleteMemosPermanently(listOf(memo.id))
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.memos.isEmpty() }

        // Assert
        assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds)
    }

    @Test
    fun toggleMemoSelectionRemovesSelectedMemo() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val viewModel = trashViewModel(memoRepository = FakeMemoRepository(listOf(memo)))
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }
        viewModel.startSelection(memo.id)

        // Act
        viewModel.toggleMemoSelection(memo.id)
        val state = viewModel.uiState.first { !it.selection.isActive }

        // Assert
        assertEquals(emptySet<MemoId>(), state.selection.selectedMemoIds)
    }

    @Test
    fun restoreSelectedMemosDelegatesSelectedMemoIds() = runTest(dispatcher) {
        // Arrange
        val memo1 = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val memo2 = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(memo1, memo2))
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.size == 2 }
        viewModel.startSelection(memo2.id)
        viewModel.toggleMemoSelection(memo1.id)
        viewModel.uiState.first {
            it.selection.selectedMemoIds == setOf(memo2.id, memo1.id)
        }

        // Act
        viewModel.restoreSelectedMemos()
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(memo2.id, memo1.id), repository.restoredIds)
    }

    @Test
    fun requestEmptyTrashShowsEmptyTrashDialog() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(memo))
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }

        // Act
        viewModel.requestEmptyTrash()
        advanceUntilIdle()

        // Assert
        assertEquals(true, viewModel.uiState.value.showEmptyTrashDialog)
    }

    @Test
    fun boundaryRequestEmptyTrashDoesNotShowDialogWhenTrashIsEmpty() = runTest(dispatcher) {
        // Arrange
        val viewModel = trashViewModel(memoRepository = FakeMemoRepository())
        advanceUntilIdle()
        viewModel.uiState.first { !it.isLoading && it.memos.isEmpty() }

        // Act
        viewModel.requestEmptyTrash()
        advanceUntilIdle()

        // Assert
        assertEquals(false, viewModel.uiState.value.showEmptyTrashDialog)
    }

    @Test
    fun confirmEmptyTrashDeletesAllVisibleTrashedMemos() = runTest(dispatcher) {
        // Arrange
        val memo1 = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val memo2 = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(memo1, memo2))
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }
        viewModel.requestEmptyTrash()

        // Act
        viewModel.confirmEmptyTrash()
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(memo2.id, memo1.id), repository.permanentlyDeletedIds)
    }

    @Test
    fun coroutineRapidConfirmEmptyTrashDoesNotEmitErrorFromReentrancy() = runTest(dispatcher) {
        // Arrange
        val memo1 = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val memo2 = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(memo1, memo2))
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }
        viewModel.requestEmptyTrash()

        // Act & Assert
        // Coroutine/Boundary: the in-flight guard blocks the second rapid confirm so the
        // already-deleted memos are not re-deleted (which would emit a spurious error).
        viewModel.actionErrorEvent.test {
            viewModel.confirmEmptyTrash()
            viewModel.confirmEmptyTrash()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(listOf(memo2.id, memo1.id), repository.permanentlyDeletedIds)
    }

    @Test
    fun flowConfirmEmptyTrashEmitsActionErrorAndHidesDialogWhenDeleteFails() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val viewModel = trashViewModel(
            memoRepository = DeleteFailingMemoRepository(listOf(memo))
        )
        advanceUntilIdle()
        viewModel.uiState.first { it.memos.isNotEmpty() }
        viewModel.requestEmptyTrash()
        viewModel.uiState.first { it.showEmptyTrashDialog }

        // Act & Assert
        // Flow/Error/StateTransition: empty-trash failure emits an event and closes the dialog.
        viewModel.actionErrorEvent.test {
            viewModel.confirmEmptyTrash()
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
            assertEquals(false, viewModel.uiState.value.showEmptyTrashDialog)
        }
    }

    @Test
    fun flowRestoreSelectedMemosEmitsActionErrorAndKeepsSelectionWhenRestoreFails() =
        runTest(dispatcher) {
            // Arrange
            val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
            val viewModel = trashViewModel(
                memoRepository = RestoreFailingMemoRepository(listOf(memo))
            )
            advanceUntilIdle()
            viewModel.uiState.first { it.memos.isNotEmpty() }
            viewModel.startSelection(memo.id)
            viewModel.uiState.first {
                it.selection.selectedMemoIds == setOf(memo.id)
            }

            // Act & Assert
            // Flow/Error/StateTransition: a failed atomic restore keeps the selection for retry.
            viewModel.actionErrorEvent.test {
                viewModel.restoreSelectedMemos()
                advanceUntilIdle()
                assertEquals(Unit, awaitItem())
                assertEquals(setOf(memo.id), viewModel.uiState.value.selection.selectedMemoIds)
            }
        }

    @Test
    fun flowUiStateHasErrorWhenObserveTrashedMemosFails() = runTest(dispatcher) {
        // Arrange
        val viewModel = trashViewModel(
            memoRepository = ObserveTrashedFailingMemoRepository()
        )

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.hasError }

        // Assert
        assertAll(
            { assertEquals(true, state.hasError) },
            { assertEquals(emptyList<MemoId>(), state.memos.map { it.id }) }
        )
    }

    @Test
    fun stateTransitionRetryClearsPurgeErrorAfterSuccessfulPurge() = runTest(dispatcher) {
        // Arrange
        val repository = PurgeFailingOnceMemoRepository()
        val viewModel = trashViewModel(memoRepository = repository)
        advanceUntilIdle()
        viewModel.uiState.first { it.hasError }

        // Act
        viewModel.retry()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.hasError }

        // Assert
        assertAll(
            { assertEquals(false, state.hasError) },
            { assertEquals(2, repository.purgeAttempts) }
        )
    }

    @Test
    fun initTriggersExpiredTrashPurge() = runTest(dispatcher) {
        // Arrange
        val repository = FakeMemoRepository(listOf(memoFixture(deletedAt = 2_000L)))

        // Act
        trashViewModel(memoRepository = repository)
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(TimestampMillis(0L)), repository.purgeCutoffs)
    }

    private fun trashViewModel(
        memoRepository: MemoRepository = FakeMemoRepository(),
        tagRepository: FakeTagRepository = FakeTagRepository(listOf(tagFixture()))
    ) = TrashViewModel(
        observeTrashedMemosUseCase = ObserveTrashedMemosUseCase(memoRepository),
        observeTagsUseCase = ObserveTagsUseCase(tagRepository),
        restoreMemosFromTrashUseCase = RestoreMemosFromTrashUseCase(memoRepository),
        deleteMemosPermanentlyUseCase = DeleteMemosPermanentlyUseCase(memoRepository),
        purgeExpiredTrashedMemosUseCase = PurgeExpiredTrashedMemosUseCase(
            memoRepository = memoRepository,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(0L))
        )
    )

    private class RestoreFailingMemoRepository(initialMemos: List<Memo>) :
        MemoRepository by FakeMemoRepository(initialMemos) {
        override suspend fun restoreMemosFromTrash(ids: List<MemoId>): Unit =
            error("Failed to restore memos.")
    }

    private class DeleteFailingMemoRepository(initialMemos: List<Memo>) :
        MemoRepository by FakeMemoRepository(initialMemos) {
        override suspend fun deleteMemosPermanently(ids: List<MemoId>): Unit =
            error("Failed to delete memos.")
    }

    private class ObserveTrashedFailingMemoRepository : MemoRepository by FakeMemoRepository() {
        override fun observeTrashedMemos(): Flow<List<Memo>> = flow {
            throw IllegalStateException("Failed to observe trashed memos.")
        }
    }

    private class PurgeFailingOnceMemoRepository(
        private val delegate: FakeMemoRepository = FakeMemoRepository()
    ) : MemoRepository by delegate {
        var purgeAttempts = 0
            private set

        override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis) {
            purgeAttempts += 1
            if (purgeAttempts == 1) error("Failed to purge trash.")
            delegate.deleteTrashedMemosDeletedAtOrBefore(cutoff)
        }
    }
}
