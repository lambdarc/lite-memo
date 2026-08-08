package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoExportToken
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.MemoExportArchiveRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PrepareMemoExportUseCaseTest {

    @Test
    fun interactionInvokeSnapshotsActiveMemosBeforePreparingArchive() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val repository = mockk<MemoExportArchiveRepository>()
        val token = MemoExportToken("prepared-1")
        coEvery { repository.prepare(any()) } returns token
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
        assertEquals(token, actual)
        coVerify(exactly = 1) { repository.prepare(match { it.memos == listOf(memo) }) }
    }

}
