package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoRepository
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
        val useCase = SearchMemosUseCase(repository, FakeDisplaySettingsRepository())

        // Act
        val memos = useCase("   ").first()

        // Assert
        assertEquals(emptyList<Memo>(), memos)
    }

    @Test
    fun invokePassesTrimmedQueryToRepository() = runTest {
        // Arrange
        val repository = SearchOnlyMemoRepository()
        val useCase = SearchMemosUseCase(repository, FakeDisplaySettingsRepository())

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
            val displaySettingsRepository = FakeDisplaySettingsRepository()
            displaySettingsRepository.setMemoSortOrder(MemoSortOrder.CREATED_NEWEST)

            // Act
            val memos = SearchMemosUseCase(repository, displaySettingsRepository)("query").first()

            // Assert
            assertEquals(listOf(newer.id, older.id), memos.map { it.id })
        }

    @Test
    fun invokeExcludesTrashedMemos() = runTest {
        // Arrange
        val active = memoFixture(id = "active", title = "shopping")
        val trashed = memoFixture(id = "trashed", title = "shopping", deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(active, trashed))
        val useCase = SearchMemosUseCase(repository, FakeDisplaySettingsRepository())

        // Act
        val memos = useCase("shopping").first()

        // Assert
        assertEquals(listOf(active.id), memos.map { it.id })
    }

    private class SearchOnlyMemoRepository(
        private val results: List<Memo> = emptyList(),
        private val failOnSearch: Boolean = false
    ) : MemoRepository by FakeMemoRepository() {

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

    }
}
