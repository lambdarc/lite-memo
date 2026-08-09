package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.TrashMoveRecord
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MoveMemoToTrashUseCaseTest {

    @Test
    fun invokeMovesActiveMemoToTrashWithCurrentTime() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val repository = FakeMemoRepository(listOf(memo))
        val useCase = MoveMemoToTrashUseCase(
            memoRepository = repository,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(2_000L))
        )

        // Act
        useCase(memo.id)

        // Assert
        val expected = TrashMoveRecord(
            memoId = memo.id,
            deletedAt = TimestampMillis(2_000L)
        )
        assertEquals(listOf(expected), repository.movedToTrash)
    }

    @Test
    fun invokeUsesCreatedAtWhenCurrentTimeIsEarlierThanCreatedAt() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1", createdAt = 2_000L, updatedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(memo))
        val useCase = MoveMemoToTrashUseCase(
            memoRepository = repository,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(1_000L))
        )

        // Act
        useCase(memo.id)

        // Assert
        val expected = TrashMoveRecord(
            memoId = memo.id,
            deletedAt = TimestampMillis(2_000L)
        )
        assertEquals(listOf(expected), repository.movedToTrash)
    }

    @Test
    fun invokeUsesUpdatedAtWhenCurrentTimeIsEarlierThanUpdatedAt() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1", createdAt = 1_000L, updatedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(memo))
        val useCase = MoveMemoToTrashUseCase(
            memoRepository = repository,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(1_500L))
        )

        // Act
        useCase(memo.id)

        // Assert
        val expected = TrashMoveRecord(
            memoId = memo.id,
            deletedAt = TimestampMillis(2_000L)
        )
        assertEquals(listOf(expected), repository.movedToTrash)
    }

    @Test
    fun invokeThrowsWhenMemoIsNotActive() {
        // Arrange
        val repository = FakeMemoRepository(listOf(memoFixture(deletedAt = 2_000L)))
        val useCase = MoveMemoToTrashUseCase(
            memoRepository = repository,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(3_000L))
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest { useCase(MemoId("memo-1")) }
        }
    }
}
