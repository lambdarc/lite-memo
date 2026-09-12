package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoExportArchiveRepository
import com.lambdarc.litememo.domain.model.value.ExportFileReference
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WriteMemoExportUseCaseTest {

    @Test
    fun interactionInvokeDelegatesTokenAndDestinationToRepository() = runTest {
        // Arrange
        val repository = FakeMemoExportArchiveRepository()
        val useCase = WriteMemoExportUseCase(repository)
        val destination = ExportFileReference("content://export.zip")

        // Act
        // Interaction: the prepared archive is written to the destination the picker returned.
        useCase(FakeMemoExportArchiveRepository.TOKEN, destination)

        // Assert
        assertEquals(
            listOf(FakeMemoExportArchiveRepository.TOKEN to destination),
            repository.writes
        )
    }

}
