package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveTrashedMemosUseCaseTest {

    @Test
    fun invokeReturnsTrashedMemosNewestDeletedFirst() = runTest {
        // Arrange
        val oldTrash = memoFixture(id = "old", deletedAt = 2_000L)
        val active = memoFixture(id = "active")
        val newTrash = memoFixture(id = "new", deletedAt = 3_000L)
        val repository = FakeMemoRepository(listOf(oldTrash, active, newTrash))
        val useCase = ObserveTrashedMemosUseCase(repository)

        // Act
        val memos = useCase().first()

        // Assert
        assertEquals(listOf(newTrash, oldTrash), memos)
    }
}
