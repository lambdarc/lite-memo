package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.ActiveMemoBulkWrite
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.MemoSummary
import com.appvoyager.litememo.domain.model.MemoTrashUpdate
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.SearchQuery
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.model.value.TimestampRange
import com.appvoyager.litememo.domain.repository.FakeUserSettingsRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class SearchMemosUseCaseTest {

    @Test
    fun invokeReturnsEmptyListWhenQueryIsBlank() = runTest {
        // Arrange
        val repository = SearchOnlyMemoRepository(failOnSearch = true)
        val useCase = SearchMemosUseCase(repository, FakeUserSettingsRepository())

        // Act
        val memos = useCase("   ").first()

        // Assert
        assertEquals(emptyList<Memo>(), memos)
    }

    @Test
    fun invokePassesTrimmedQueryToRepository() = runTest {
        // Arrange
        val repository = SearchOnlyMemoRepository()
        val useCase = SearchMemosUseCase(repository, FakeUserSettingsRepository())

        // Act
        useCase("  shopping  ").first()

        // Assert
        assertEquals(SearchQuery("shopping"), repository.observedQuery)
    }

    @Test
    fun invokeReturnsSearchResultsSortedByCreatedAtDescendingWhenSortOrderIsCreatedNewest() =
        runTest {
            // Arrange
            val older = memoFixture(id = "older", createdAt = 1_000L, updatedAt = 3_000L)
            val newer = memoFixture(id = "newer", createdAt = 2_000L, updatedAt = 2_000L)
            val repository = SearchOnlyMemoRepository(results = listOf(older, newer))
            val userSettingsRepository = FakeUserSettingsRepository()
            userSettingsRepository.setMemoSortOrder(MemoSortOrder.CREATED_NEWEST)

            // Act
            val memos = SearchMemosUseCase(repository, userSettingsRepository)("query").first()

            // Assert
            assertEquals(listOf(newer.id, older.id), memos.map { it.id })
        }

    @Test
    fun invokeExcludesTrashedMemos() = runTest {
        // Arrange
        val active = memoFixture(id = "active", title = "shopping")
        val trashed = memoFixture(id = "trashed", title = "shopping", deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(active, trashed))
        val useCase = SearchMemosUseCase(repository, FakeUserSettingsRepository())

        // Act
        val memos = useCase("shopping").first()

        // Assert
        assertEquals(listOf(active.id), memos.map { it.id })
    }

    private class SearchOnlyMemoRepository(
        private val results: List<Memo> = emptyList(),
        private val failOnSearch: Boolean = false
    ) : MemoRepository {

        var observedQuery: SearchQuery? = null

        override fun observeActiveMemos(): Flow<List<Memo>> = flow {
            fail<Nothing>("observeActiveMemos should not be called.")
        }

        override fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummary>> = flow {
            fail<Nothing>("observeRecentActiveMemos should not be called.")
        }

        override fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>> {
            if (failOnSearch) {
                fail<Nothing>("observeActiveMemosBySearchQuery should not be called.")
            }
            observedQuery = query
            return flowOf(results)
        }

        override fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>> =
            flowOf(emptyList())

        override fun observeTrashedMemos(): Flow<List<Memo>> = flowOf(emptyList())

        override suspend fun getActiveMemo(id: MemoId): Memo? = null

        override suspend fun getActiveMemos(ids: List<MemoId>): List<Memo> = emptyList()

        override suspend fun saveMemo(memo: Memo) = Unit

        override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>) = Unit

        override suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis) = Unit

        override suspend fun moveMemosToTrash(updates: List<MemoTrashUpdate>) = Unit

        override suspend fun restoreMemoFromTrash(id: MemoId) = Unit

        override suspend fun restoreMemosFromTrash(ids: List<MemoId>) = Unit

        override suspend fun deleteMemoPermanently(id: MemoId) = Unit

        override suspend fun deleteMemosPermanently(ids: List<MemoId>) = Unit

        override suspend fun discardMemo(id: MemoId) = Unit

        override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: TimestampMillis) = Unit

        override suspend fun getAllActiveMemos(): List<Memo> = emptyList()

        override suspend fun saveAllMemos(memos: List<Memo>) = Unit

    }
}
