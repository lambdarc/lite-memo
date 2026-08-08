package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.repository.MemoRepository
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeleteMemosPermanentlyUseCaseTest {

    @Test
    fun normalInvokeDeletesDistinctMemosInInputOrder() = runTest {
        // Arrange
        val first = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val second = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(first, second))
        val useCase = DeleteMemosPermanentlyUseCase(repository)

        // Act
        // Normal: duplicate IDs are removed without changing first-occurrence order.
        useCase(listOf(second.id, first.id, second.id))

        // Assert
        assertEquals(listOf(second.id, first.id), repository.permanentlyDeletedIds)
    }

    @Test
    fun boundaryEmptyIdsSkipsRepositoryWrite() = runTest {
        // Arrange
        val repository = mockk<MemoRepository>()
        val useCase = DeleteMemosPermanentlyUseCase(repository)

        // Act
        // Boundary/Interaction: empty input is a pure no-op.
        useCase(emptyList())

        // Assert
        coVerify(exactly = 0) { repository.deleteMemosPermanently(any()) }
        confirmVerified(repository)
    }

    @Test
    fun boundaryAbsentIdsAreSkippedWhileValidMemosAreDeleted() = runTest {
        // Arrange
        val first = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val second = memoFixture(id = "memo-2", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(first, second))
        val useCase = DeleteMemosPermanentlyUseCase(repository)

        // Act
        // Boundary: an id no longer in trash is skipped instead of failing the whole batch.
        useCase(listOf(first.id, MemoId("missing"), second.id))

        // Assert
        assertAll(
            { assertEquals(listOf(first.id, second.id), repository.permanentlyDeletedIds) },
            { assertEquals(emptyList<MemoId>(), repository.currentMemos().map { it.id }) }
        )
    }
}
