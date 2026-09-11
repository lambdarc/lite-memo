package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.ApplyMemoBulkActionCommand
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoBulkAction
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoConcurrencyTest {

    @Test
    fun errorBulkFavoriteRejectsConcurrentEditWithUnchangedTimestamp() = runTest {
        // Arrange
        val original = memoFixture(updatedAt = 5_000L)
        val edited = original.copy(body = MemoBody("Concurrent edit"))
        val delegate = FakeMemoRepository(listOf(original))
        val useCase = ApplyMemoBulkActionUseCase(
            replacingBeforeWrite(delegate, edited),
            FakeTagRepository(),
            MutableTimeProvider(TimestampMillis(3_000L))
        )

        // Act
        // Error: bulk writes also compare content when timestamps are identical.
        val error = runCatching {
            useCase(
                ApplyMemoBulkActionCommand(listOf(original.id), MemoBulkAction.SetFavorite(true))
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(listOf(edited), delegate.currentMemos()) }
        )
    }

    @Test
    fun errorBulkFavoriteDoesNotRestoreConcurrentlyTrashedMemo() = runTest {
        // Arrange
        val original = memoFixture()
        val trashed = original.copy(deletedAt = TimestampMillis(2_000L))
        val delegate = FakeMemoRepository(listOf(original))
        val useCase = ApplyMemoBulkActionUseCase(
            replacingBeforeWrite(delegate, trashed),
            FakeTagRepository(),
            MutableTimeProvider(TimestampMillis(3_000L))
        )

        // Act
        // Error: trashing after the read prevents the favorite write.
        val error = runCatching {
            useCase(
                ApplyMemoBulkActionCommand(listOf(original.id), MemoBulkAction.SetFavorite(true))
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(listOf(trashed), delegate.currentMemos()) }
        )
    }

    private fun replacingBeforeWrite(
        delegate: FakeMemoRepository,
        replacement: Memo
    ): MemoRepository = object : MemoRepository by delegate {
        override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>) {
            delegate.saveMemo(replacement)
            delegate.saveActiveMemoBulkWrites(writes)
        }
    }

}
