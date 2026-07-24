package com.appvoyager.litememo.ui.viewmodel

import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoSummary
import com.appvoyager.litememo.domain.model.MemoTrashUpdate
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.SearchQuery
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.model.value.TimestampRange
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.usecase.RestoreMemoFromTrashUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiteMemoAppViewModelTest {

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
    fun restoreMemoEmitsErrorEventWhenRestoreFails() = runTest(dispatcher) {
        // Arrange
        val repository = ThrowingRestoreMemoRepository(
            IllegalStateException("Restore failed.")
        )
        val viewModel = LiteMemoAppViewModel(
            restoreMemoFromTrashUseCase = RestoreMemoFromTrashUseCase(repository)
        )

        // Act
        viewModel.restoreMemo(MemoId("memo-1"))
        advanceUntilIdle()
        val event = viewModel.restoreMemoErrorEvent.first()

        // Assert
        assertEquals(Unit, event)
    }

    @Test
    fun restoreMemoDoesNotEmitErrorEventWhenRestoreIsCancelled() = runTest(dispatcher) {
        // Arrange
        val repository = ThrowingRestoreMemoRepository(
            CancellationException("Cancelled.")
        )
        val viewModel = LiteMemoAppViewModel(
            restoreMemoFromTrashUseCase = RestoreMemoFromTrashUseCase(repository)
        )

        // Act
        viewModel.restoreMemo(MemoId("memo-1"))
        advanceUntilIdle()
        val event = withTimeoutOrNull(100L) {
            viewModel.restoreMemoErrorEvent.first()
        }

        // Assert
        assertNull(event)
    }

    private class ThrowingRestoreMemoRepository(private val throwable: Throwable) :
        MemoRepository {

        override fun observeActiveMemos(): Flow<List<Memo>> = flowOf(emptyList())

        override fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummary>> =
            flowOf(emptyList())

        override fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>> =
            flowOf(emptyList())

        override fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>> =
            flowOf(emptyList())

        override fun observeTrashedMemos(): Flow<List<Memo>> = flowOf(emptyList())

        override suspend fun getActiveMemo(id: MemoId): Memo? = null

        override suspend fun getActiveMemos(ids: List<MemoId>): List<Memo> = emptyList()

        override suspend fun saveMemo(memo: Memo): Unit = throw throwable

        override suspend fun saveAllActiveMemos(
            expectedActiveIds: List<MemoId>,
            memos: List<Memo>
        ) = Unit

        override suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis) = Unit

        override suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>) = Unit

        override suspend fun restoreMemoFromTrash(id: MemoId): Unit = throw throwable

        override suspend fun restoreMemosFromTrash(ids: List<MemoId>) = Unit

        override suspend fun deleteMemoPermanently(id: MemoId) = Unit

        override suspend fun deleteMemosPermanently(ids: List<MemoId>) = Unit

        override suspend fun discardMemo(id: MemoId) = Unit

        override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis) = Unit

        override suspend fun getAllActiveMemos(): List<Memo> = emptyList()

        override suspend fun saveAllMemos(memos: List<Memo>) = Unit

    }
}
