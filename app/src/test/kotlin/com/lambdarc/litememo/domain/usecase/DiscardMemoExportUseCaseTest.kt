package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoExportArchiveRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiscardMemoExportUseCaseTest {

    @Test
    fun interactionInvokeDelegatesTokenToRepository() = runTest {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val useCase = DiscardMemoExportUseCase(repository)

        // Act
        // Interaction: an abandoned prepared archive is discarded by its token.
        useCase(FakeMemoExportArchiveRepository.TOKEN)

        // Assert
        assertEquals(listOf(FakeMemoExportArchiveRepository.TOKEN), repository.discardedTokens)
    }

}
