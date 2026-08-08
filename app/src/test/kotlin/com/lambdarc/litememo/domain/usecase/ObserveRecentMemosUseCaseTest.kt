package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveRecentMemosUseCaseTest {

    @Test
    fun normalInvokeReturnsLimitedMemoSummaries() = runTest {
        // Arrange
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "old", updatedAt = 1_000L),
                memoFixture(id = "new", updatedAt = 3_000L),
                memoFixture(id = "middle", updatedAt = 2_000L)
            )
        )

        // Act
        // Normal: the requested limit is applied while returning the summary contract
        val summaries = ObserveRecentMemosUseCase(repository)(limit = 2).first()

        // Assert
        assertEquals(listOf(MemoId("new"), MemoId("middle")), summaries.map { it.id })
    }
}
