package com.appvoyager.litememo.data.repository

import com.appvoyager.litememo.data.export.MemoArchiveException
import com.appvoyager.litememo.data.export.MemoArchiveFailureReason
import com.appvoyager.litememo.data.export.MemoImportArchiveExtractor
import com.appvoyager.litememo.data.export.MemoImportSessionDataSource
import com.appvoyager.litememo.data.image.MemoImageFileDataSource
import com.appvoyager.litememo.data.local.dao.MemoDao
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StagingMemoImportArchiveRepositoryTest {

    @Test
    fun coroutineStageCancellationRemainsPrimaryWhenRollbackFails() {
        // Arrange
        val reference = ExportFileReference("content://import.zip")
        val token = MemoImportSessionToken("session-1")
        val cancellation = CancellationException("cancelled")
        val rollbackFailure = IllegalStateException("rollback failed")
        val extractor = mockk<MemoImportArchiveExtractor>()
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery { sessionDataSource.open() } returns token
        every { extractor.extractImages(reference, token) } throws cancellation
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${token.value}-")
        } throws rollbackFailure
        val repository = StagingMemoImportArchiveRepository(
            extractor = extractor,
            sessionDataSource = sessionDataSource,
            imageFileDataSource = imageFileDataSource,
            memoDao = mockk<MemoDao>(),
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // Act
        // Coroutine: staging cancellation stays primary when cleanup also fails.
        val actual = assertThrows(CancellationException::class.java) {
            runTest { repository.stageImportImages(reference) }
        }

        // Assert
        assertAll(
            { assertSame(cancellation, actual) },
            { assertEquals(rollbackFailure.message, actual.suppressed.single().message) }
        )
    }

    @Test
    fun errorMappedArchiveFailureContainsRollbackFailureAsSuppressed() {
        // Arrange
        val reference = ExportFileReference("content://import.zip")
        val token = MemoImportSessionToken("session-1")
        val archiveFailure = MemoArchiveException(
            MemoArchiveFailureReason.MALFORMED_ARCHIVE,
            "invalid archive"
        )
        val rollbackFailure = IllegalStateException("rollback failed")
        val extractor = mockk<MemoImportArchiveExtractor>()
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery { sessionDataSource.open() } returns token
        every { extractor.extractImages(reference, token) } throws archiveFailure
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${token.value}-")
        } throws rollbackFailure
        val repository = StagingMemoImportArchiveRepository(
            extractor = extractor,
            sessionDataSource = sessionDataSource,
            imageFileDataSource = imageFileDataSource,
            memoDao = mockk<MemoDao>(),
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // Act
        // Error: cleanup diagnostics remain visible on the mapped exception callers receive.
        val actual = assertThrows(MemoImportException::class.java) {
            runTest { repository.stageImportImages(reference) }
        }

        // Assert
        assertAll(
            { assertEquals(MemoImportFailureReason.INVALID_ARCHIVE, actual.reason) },
            { assertSame(archiveFailure, actual.cause) },
            { assertEquals(rollbackFailure.message, actual.suppressed.single().message) }
        )
    }

    @Test
    fun errorCleanupContinuesWithLaterSessionWhenImageCleanupFails() {
        // Arrange
        val firstToken = MemoImportSessionToken("session-1")
        val secondToken = MemoImportSessionToken("session-2")
        val cleanupFailure = IllegalStateException("image cleanup failed")
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery {
            sessionDataSource.claimAbandonedTokens()
        } returns listOf(firstToken, secondToken)
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${firstToken.value}-")
        } throws cleanupFailure
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${secondToken.value}-")
        } returns emptyList()
        coEvery { sessionDataSource.close(secondToken) } returns Unit
        val repository = repository(sessionDataSource, imageFileDataSource)

        // Act
        // Error/Interaction: one broken session does not block cleanup of later sessions.
        val actual = assertThrows(IllegalStateException::class.java) {
            runTest { repository.deleteUnreferencedImportImages() }
        }

        // Assert
        assertSame(cleanupFailure, actual)
        coVerify(exactly = 0) { sessionDataSource.close(firstToken) }
        coVerify(exactly = 1) { sessionDataSource.close(secondToken) }
    }

    @Test
    fun errorCleanupContinuesWithLaterSessionWhenSessionCloseFails() {
        // Arrange
        val firstToken = MemoImportSessionToken("session-1")
        val secondToken = MemoImportSessionToken("session-2")
        val closeFailure = IllegalStateException("session close failed")
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery {
            sessionDataSource.claimAbandonedTokens()
        } returns listOf(firstToken, secondToken)
        coEvery { imageFileDataSource.listImageFileNamesStartingWith(any()) } returns emptyList()
        coEvery { sessionDataSource.close(firstToken) } throws closeFailure
        coEvery { sessionDataSource.close(secondToken) } returns Unit
        val repository = repository(sessionDataSource, imageFileDataSource)

        // Act
        // Error/Interaction: a close failure does not block later session cleanup.
        val actual = assertThrows(IllegalStateException::class.java) {
            runTest { repository.deleteUnreferencedImportImages() }
        }

        // Assert
        assertSame(closeFailure, actual)
        coVerify(exactly = 1) { sessionDataSource.close(firstToken) }
        coVerify(exactly = 1) { sessionDataSource.close(secondToken) }
    }

    @Test
    fun coroutineCleanupCancellationStopsLaterSessions() {
        // Arrange
        val firstToken = MemoImportSessionToken("session-1")
        val secondToken = MemoImportSessionToken("session-2")
        val cancellation = CancellationException("cancelled")
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery {
            sessionDataSource.claimAbandonedTokens()
        } returns listOf(firstToken, secondToken)
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${firstToken.value}-")
        } throws cancellation
        val repository = repository(sessionDataSource, imageFileDataSource)

        // Act
        // Coroutine: cancellation stops cleanup instead of being aggregated as an ordinary failure.
        val actual = assertThrows(CancellationException::class.java) {
            runTest { repository.deleteUnreferencedImportImages() }
        }

        // Assert
        assertSame(cancellation, actual)
        coVerify(exactly = 0) {
            imageFileDataSource.listImageFileNamesStartingWith("${secondToken.value}-")
        }
    }

    private fun repository(
        sessionDataSource: MemoImportSessionDataSource,
        imageFileDataSource: MemoImageFileDataSource
    ) = StagingMemoImportArchiveRepository(
        extractor = mockk<MemoImportArchiveExtractor>(),
        sessionDataSource = sessionDataSource,
        imageFileDataSource = imageFileDataSource,
        memoDao = mockk<MemoDao>(),
        ioDispatcher = UnconfinedTestDispatcher()
    )

}
