package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.FakeTagRepository
import com.appvoyager.litememo.domain.MutableTimeProvider
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.value.MemoExportToken
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
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
