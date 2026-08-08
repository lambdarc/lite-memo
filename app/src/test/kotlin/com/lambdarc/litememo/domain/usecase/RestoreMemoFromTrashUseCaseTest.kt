package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RestoreMemoFromTrashUseCaseTest {

    @Test
    fun invokeRestoresMemoFromTrashById() = runTest {
        // Arrange
        val memo = memoFixture(deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(memo))
        val useCase = RestoreMemoFromTrashUseCase(repository)

        // Act
        useCase(memo.id)

        // Assert
        assertEquals(listOf(memo.id), repository.restoredIds)
    }
}
