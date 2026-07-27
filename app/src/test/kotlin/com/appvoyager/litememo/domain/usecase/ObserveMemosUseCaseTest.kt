package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.repository.FakeDisplaySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveMemosUseCaseTest {

    @Test
    fun invokeReturnsMemosSortedByUpdatedAtDescending() = runTest {
        // Arrange
        val older = memoFixture(id = "older", updatedAt = 1000L)
        val newer = memoFixture(id = "newer", updatedAt = 2000L)
        val repository = FakeMemoRepository(listOf(older, newer))

        // Act
        val memos = ObserveMemosUseCase(repository, FakeDisplaySettingsRepository())().first()

        // Assert
        assertEquals(listOf(newer.id, older.id), memos.map { it.id })
    }

    @Test
    fun invokeExcludesTrashedMemos() = runTest {
        // Arrange
        val active = memoFixture(id = "active")
        val trashed = memoFixture(id = "trashed", deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(active, trashed))

        // Act
        val memos = ObserveMemosUseCase(repository, FakeDisplaySettingsRepository())().first()

        // Assert
        assertEquals(listOf(active.id), memos.map { it.id })
    }

    @Test
    fun invokeReturnsFavoriteMemosBeforeNormalMemos() = runTest {
        // Arrange
        val normal = memoFixture(id = "normal", updatedAt = 2000L)
        val favorite = memoFixture(id = "favorite", updatedAt = 1000L, isFavorite = true)
        val repository = FakeMemoRepository(listOf(normal, favorite))

        // Act
        val memos = ObserveMemosUseCase(repository, FakeDisplaySettingsRepository())().first()

        // Assert
        assertEquals(listOf(favorite.id, normal.id), memos.map { it.id })
    }

    @Test
    fun invokeReturnsFavoriteMemosBeforeNormalMemosWhenSortedByCreatedAt() = runTest {
        // Arrange
        val normal = memoFixture(id = "normal", createdAt = 2000L)
        val favorite = memoFixture(id = "favorite", createdAt = 1000L, isFavorite = true)
        val repository = FakeMemoRepository(listOf(normal, favorite))
        val settingsRepository = FakeDisplaySettingsRepository()
        settingsRepository.setMemoSortOrder(MemoSortOrder.CREATED_NEWEST)

        // Act
        val memos = ObserveMemosUseCase(repository, settingsRepository)().first()

        // Assert
        assertEquals(listOf(favorite.id, normal.id), memos.map { it.id })
    }

}
