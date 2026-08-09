package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GetMemoUseCaseTest {

    @Test
    fun invokeReturnsActiveMemoById() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val repository = FakeMemoRepository(listOf(memo))

        // Act
        val result = GetMemoUseCase(repository)(memo.id)

        // Assert
        assertEquals(memo, result)
    }

    @Test
    fun invokeReturnsNullWhenMemoIsTrashed() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1", deletedAt = 2_000L)
        val repository = FakeMemoRepository(listOf(memo))

        // Act
        val result = GetMemoUseCase(repository)(memo.id)

        // Assert
        assertNull(result)
    }
}
