package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.QueueMemoIdProvider
import com.lambdarc.litememo.domain.model.value.MemoId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenerateMemoIdUseCaseTest {

    @Test
    fun normalInvokeReturnsProviderMemoId() {
        // Arrange
        val useCase = GenerateMemoIdUseCase(
            QueueMemoIdProvider(listOf(MemoId("generated-id")))
        )

        // Act
        // Normal: generated ids are delegated to the domain provider.
        val memoId = useCase()

        // Assert
        assertEquals(MemoId("generated-id"), memoId)
    }

}
