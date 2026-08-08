package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscardMemoUseCaseTest {

    @Test
    fun normalInvokeDeletesMemoRegardlessOfTrashState() = runTest {
        // Arrange
        val repository = FakeMemoRepository(listOf(memoFixture(id = "memo-1")))
        val useCase = DiscardMemoUseCase(repository)

        // Act
        // Normal: discard is an unconditional hard delete for abandoned edit rows.
        useCase(MemoId("memo-1"))

        // Assert
        assertEquals(emptyList<MemoId>(), repository.currentMemos().map { it.id })
    }

    @Test
    fun interactionInvokeDelegatesMemoIdToRepository() = runTest {
        // Arrange
        val repository = FakeMemoRepository()
        val useCase = DiscardMemoUseCase(repository)

        // Act
        // Interaction: missing rows are no-op at repository level.
        useCase(MemoId("missing"))

        // Assert
        assertEquals(listOf(MemoId("missing")), repository.discardedIds)
    }

}
