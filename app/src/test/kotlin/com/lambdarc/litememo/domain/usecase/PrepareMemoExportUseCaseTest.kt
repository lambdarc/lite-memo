package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoExportArchiveRepository
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrepareMemoExportUseCaseTest {

    @Test
    fun interactionInvokeSnapshotsActiveMemosBeforePreparingArchive() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val repository = FakeMemoExportArchiveRepository()
        val useCase = PrepareMemoExportUseCase(
            ExportMemosUseCase(
                FakeMemoRepository(listOf(memo)),
                FakeTagRepository(),
                MutableTimeProvider(TimestampMillis(1_000L))
            ),
            repository
        )

        // Act
        // Interaction: the active snapshot is passed once to archive preparation.
        val actual = useCase()

        // Assert
        assertAll(
            { assertEquals(FakeMemoExportArchiveRepository.TOKEN, actual) },
            { assertEquals(listOf(memo), repository.preparedData.single().memos) }
        )
    }

}
