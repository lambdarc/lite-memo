package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoImportArchiveRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeleteUnreferencedImportImagesUseCaseTest {

    @Test
    fun interactionInvokeDelegatesCleanupToRepository() = runTest {
        // Arrange
        val repository = FakeMemoImportArchiveRepository()
        val useCase = DeleteUnreferencedImportImagesUseCase(repository)

        // Act
        // Interaction: startup cleanup reaches the repository without a token.
        useCase()

        // Assert
        assertEquals(1, repository.deleteUnreferencedCallCount)
    }

}
