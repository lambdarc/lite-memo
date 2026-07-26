package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoImportArchiveRepository
import com.appvoyager.litememo.domain.FakeMemoImportRepository
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.StagedMemoImport
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.repository.MemoImportArchiveRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImportMemosFromFileUseCaseTest {

    @Test
    fun normalInvokeImportsStagedArchiveData() = runTest {
        // Arrange
        val data = exportData()
        val archiveRepository = FakeMemoImportArchiveRepository(stagedData = data)
        val importRepository = FakeMemoImportRepository()
        val useCase = useCase(archiveRepository, ImportMemosUseCase(importRepository))

        // Act
        // Normal: ZIP data is staged before persistence.
        useCase(REFERENCE)

        // Assert
        assertEquals(data, importRepository.importedData.single())
    }

    @Test
    fun interactionInvokeCompletesStagedImportAfterPersistence() = runTest {
        // Arrange
        val archiveRepository = FakeMemoImportArchiveRepository(stagedData = exportData())
        val useCase = useCase(archiveRepository, ImportMemosUseCase(FakeMemoImportRepository()))

        // Act
        // Interaction: a successful import completes its staging session.
        useCase(REFERENCE)

        // Assert
        assertEquals(
            listOf(FakeMemoImportArchiveRepository.TOKEN),
            archiveRepository.completedTokens
        )
    }

    @Test
    fun errorInvokeRollsBackWhenPersistenceFails() = runTest {
        // Arrange
        val archiveRepository = FakeMemoImportArchiveRepository(stagedData = exportData())
        val importUseCase = mockk<ImportMemosUseCase>()
        coEvery { importUseCase(any()) } throws IllegalStateException("db failed")
        val useCase = useCase(archiveRepository, importUseCase)

        // Act
        // Error: persistence failure rolls back staged images.
        runCatching { useCase(REFERENCE) }

        // Assert
        assertEquals(
            listOf(FakeMemoImportArchiveRepository.TOKEN),
            archiveRepository.rolledBackTokens
        )
    }

    @Test
    fun errorPersistenceFailureRemainsPrimaryWhenRollbackFails() {
        // Arrange
        val persistenceFailure = IllegalStateException("db failed")
        val rollbackFailure = IllegalStateException("rollback failed")
        val archiveRepository = mockk<MemoImportArchiveRepository>()
        val importUseCase = mockk<ImportMemosUseCase>()
        coEvery { archiveRepository.stageImportImages(REFERENCE) } returns staged(exportData())
        coEvery { importUseCase(any()) } throws persistenceFailure
        coEvery { archiveRepository.rollbackStagedImport(any()) } answers { throw rollbackFailure }
        val useCase = useCase(archiveRepository, importUseCase)

        // Act
        // Error: cleanup diagnostics do not replace the import failure.
        val actual =
            assertThrows(IllegalStateException::class.java) { runTest { useCase(REFERENCE) } }

        // Assert
        assertAll(
            { assertSame(persistenceFailure, actual) },
            { assertEquals(rollbackFailure.message, actual.suppressed.single().message) }
        )
    }

    @Test
    fun coroutineCancellationRemainsPrimaryWhenRollbackFails() {
        // Arrange
        val cancellation = CancellationException("cancelled")
        val archiveRepository = mockk<MemoImportArchiveRepository>()
        val importUseCase = mockk<ImportMemosUseCase>()
        coEvery { archiveRepository.stageImportImages(REFERENCE) } returns staged(exportData())
        coEvery { importUseCase(any()) } throws cancellation
        coEvery { archiveRepository.rollbackStagedImport(any()) } throws IllegalStateException()
        val useCase = useCase(archiveRepository, importUseCase)

        // Act
        // Coroutine: cancellation is preserved after best-effort rollback.
        val actual =
            assertThrows(CancellationException::class.java) { runTest { useCase(REFERENCE) } }

        // Assert
        assertSame(cancellation, actual)
    }

    @Test
    fun errorStagingFailureSkipsPersistenceAndCompletion() = runTest {
        // Arrange
        val archiveRepository = FakeMemoImportArchiveRepository(stagedData = exportData())
        archiveRepository.stageError = IllegalStateException("staging failed")
        val importUseCase = mockk<ImportMemosUseCase>(relaxed = true)
        val useCase = useCase(archiveRepository, importUseCase)

        // Act
        // Error/Interaction: failed ZIP staging starts neither DB import nor completion.
        runCatching { useCase(REFERENCE) }

        // Assert
        coVerify(exactly = 0) { importUseCase(any()) }
        assertEquals(emptyList<MemoImportSessionToken>(), archiveRepository.completedTokens)
    }

    private fun useCase(
        repository: MemoImportArchiveRepository,
        importUseCase: ImportMemosUseCase
    ) = ImportMemosFromFileUseCase(repository, importUseCase)

    private fun exportData() = ExportData(
        version = 1,
        exportedAt = TimestampMillis(1_000L),
        tags = emptyList(),
        memos = listOf(memoFixture())
    )

    private fun staged(data: ExportData) = StagedMemoImport(
        token = FakeMemoImportArchiveRepository.TOKEN,
        data = data
    )

    private companion object {
        val REFERENCE = ExportFileReference("content://import.zip")
    }

}
