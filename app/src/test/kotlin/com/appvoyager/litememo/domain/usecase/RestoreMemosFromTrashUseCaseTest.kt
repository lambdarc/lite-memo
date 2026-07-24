package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.repository.MemoRepository
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RestoreMemosFromTrashUseCaseTest {

    @Test
    fun normalInvokeRestoresDistinctMemosInInputOrder() = runTest {
        // Arrange
        val first = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val second = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(first, second))
        val useCase = RestoreMemosFromTrashUseCase(repository)

        // Act
        // Normal: duplicate IDs are removed without changing first-occurrence order.
        useCase(listOf(second.id, first.id, second.id))

        // Assert
        assertEquals(listOf(second.id, first.id), repository.restoredIds)
    }

    @Test
    fun boundaryEmptyIdsSkipsRepositoryWrite() = runTest {
        // Arrange
        val repository = mockk<MemoRepository>()
        val useCase = RestoreMemosFromTrashUseCase(repository)

        // Act
        // Boundary/Interaction: empty input is a pure no-op.
        useCase(emptyList())

        // Assert
        coVerify(exactly = 0) { repository.restoreMemosFromTrash(any()) }
        confirmVerified(repository)
    }

    @Test
    fun errorInvalidIdLeavesAllMemosInTrash() = runTest {
        // Arrange
        val first = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val second = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(first, second))
        val useCase = RestoreMemosFromTrashUseCase(repository)

        // Act
        // Error: validation completes before any restore is applied.
        val error = runCatching {
            useCase(listOf(first.id, MemoId("missing"), second.id))
        }.exceptionOrNull()

        // Assert
        assertEquals(
            IllegalArgumentException::class.java to listOf(first.deletedAt, second.deletedAt),
            error?.javaClass to repository.currentMemos().map { it.deletedAt }
        )
    }
}
