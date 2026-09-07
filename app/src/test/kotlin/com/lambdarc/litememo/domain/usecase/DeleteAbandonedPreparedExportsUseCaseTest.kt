package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoExportArchiveRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeleteAbandonedPreparedExportsUseCaseTest {

    @Test
    fun interactionInvokeDelegatesCleanupToRepository() = runTest {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val useCase = DeleteAbandonedPreparedExportsUseCase(repository)

        // Act
        // Interaction: startup cleanup reaches the repository without a token.
        useCase()

        // Assert
        assertEquals(1, repository.deleteAbandonedCallCount)
    }

}
