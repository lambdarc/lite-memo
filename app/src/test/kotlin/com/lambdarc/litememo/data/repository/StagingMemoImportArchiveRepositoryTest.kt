package com.lambdarc.litememo.data.repository

import com.lambdarc.litememo.data.export.MemoArchiveException
import com.lambdarc.litememo.data.export.MemoArchiveFailureReason
import com.lambdarc.litememo.data.export.MemoImportArchiveExtractor
import com.lambdarc.litememo.data.export.MemoImportSessionDataSource
import com.lambdarc.litememo.data.export.compressedImageBytes
import com.lambdarc.litememo.data.export.imageDtoFixture
import com.lambdarc.litememo.data.export.manifestFixture
import com.lambdarc.litememo.data.export.memoDtoFixture
import com.lambdarc.litememo.data.image.MemoImageFileDataSource
import com.lambdarc.litememo.data.local.dao.MemoDao
import com.lambdarc.litememo.data.model.export.LiteMemoExportDto
import com.lambdarc.litememo.domain.exception.MemoImportException
import com.lambdarc.litememo.domain.exception.MemoImportFailureReason
import com.lambdarc.litememo.domain.model.value.ExportFileReference
import com.lambdarc.litememo.domain.model.value.MemoImportSessionToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.io.IOException

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

    @Test
    fun errorCleanupKeepsSessionWhenImageDeleteFails() {
        // Arrange
        val token = MemoImportSessionToken("session-1")
        val fileName = "${token.value}-0001.jpg"
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        val memoDao = mockk<MemoDao>()
        coEvery { sessionDataSource.claimAbandonedTokens() } returns listOf(token)
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${token.value}-")
        } returns listOf(fileName)
        coEvery { memoDao.findReferencedImageFileNames(any()) } returns emptyList()
        coEvery { imageFileDataSource.deleteImage(fileName) } returns false
        val repository = StagingMemoImportArchiveRepository(
            extractor = mockk<MemoImportArchiveExtractor>(),
            sessionDataSource = sessionDataSource,
            imageFileDataSource = imageFileDataSource,
            memoDao = memoDao,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // Act
        // Error: a leftover file keeps the session open so a later sweep can retry it.
        assertThrows(IOException::class.java) {
            runTest { repository.deleteUnreferencedImportImages() }
        }

        // Assert
        coVerify(exactly = 0) { sessionDataSource.close(token) }
    }

    @Test
    fun errorCleanupReportsLaterSessionFailuresAsSuppressed() {
        // Arrange
        val firstToken = MemoImportSessionToken("session-1")
        val secondToken = MemoImportSessionToken("session-2")
        val firstFailure = IllegalStateException("first cleanup failed")
        val secondFailure = IllegalStateException("second cleanup failed")
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery {
            sessionDataSource.claimAbandonedTokens()
        } returns listOf(firstToken, secondToken)
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${firstToken.value}-")
        } throws firstFailure
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${secondToken.value}-")
        } throws secondFailure
        val repository = repository(sessionDataSource, imageFileDataSource)

        // Act
        // Error: the first failure is thrown and later ones stay visible as suppressed
        val actual = assertThrows(IllegalStateException::class.java) {
            runTest { repository.deleteUnreferencedImportImages() }
        }

        // Assert
        assertAll(
            { assertSame(firstFailure, actual) },
            { assertEquals(secondFailure.message, actual.suppressed.single().message) }
        )
    }

    @Test
    fun normalCleanupDeletesOnlyUnreferencedImagesAndClosesSession() = runTest {
        // Arrange
        val token = MemoImportSessionToken("session-1")
        val unreferenced = "${token.value}-00000001.jpg"
        val referenced = "${token.value}-00000002.jpg"
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        val memoDao = mockk<MemoDao>()
        coEvery { sessionDataSource.claimAbandonedTokens() } returns listOf(token)
        coEvery { sessionDataSource.close(token) } returns Unit
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${token.value}-")
        } returns listOf(unreferenced, referenced)
        coEvery { memoDao.findReferencedImageFileNames(any()) } returns listOf(referenced)
        coEvery { imageFileDataSource.deleteImage(unreferenced) } returns true
        val repository = StagingMemoImportArchiveRepository(
            extractor = mockk<MemoImportArchiveExtractor>(),
            sessionDataSource = sessionDataSource,
            imageFileDataSource = imageFileDataSource,
            memoDao = memoDao,
            ioDispatcher = UnconfinedTestDispatcher()
        )

        // Act
        // Normal: images a memo still references survive the sweep
        repository.deleteUnreferencedImportImages()

        // Assert
        coVerify(exactly = 1) { imageFileDataSource.deleteImage(unreferenced) }
        coVerify(exactly = 0) { imageFileDataSource.deleteImage(referenced) }
        coVerify(exactly = 1) { sessionDataSource.close(token) }
    }

    @Test
    fun normalCompleteStagedImportClosesSession() = runTest {
        // Arrange
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        coEvery { sessionDataSource.close(TOKEN) } returns Unit
        val repository = repository(sessionDataSource, mockk())

        // Act
        // Normal: completing an import releases its session without touching images
        repository.completeStagedImport(TOKEN)

        // Assert
        coVerify(exactly = 1) { sessionDataSource.close(TOKEN) }
    }

    @ParameterizedTest
    @MethodSource("archiveFailureReasons")
    fun errorStageMapsEveryArchiveFailureReason(reasonName: String) {
        // Arrange
        val reason = MemoArchiveFailureReason.valueOf(reasonName)
        val archiveFailure = MemoArchiveException(reason, "archive rejected")
        val repository = failingStageRepository(archiveFailure)

        // Act
        // Error: every archive failure reason maps to the import reason the UI explains
        val actual = assertThrows(MemoImportException::class.java) {
            runTest { repository.stageImportImages(REFERENCE) }
        }

        // Assert
        assertAll(
            { assertEquals(EXPECTED_IMPORT_REASONS[reason], actual.reason) },
            { assertSame(archiveFailure, actual.cause) }
        )
    }

    @ParameterizedTest
    @MethodSource("outOfSpaceFailures")
    fun errorStageMapsOutOfSpaceIOExceptionToInsufficientStorage(failure: IOException) {
        // Arrange
        val repository = failingStageRepository(failure)

        // Act
        // Error: ENOSPC is detected by errno or message anywhere in the cause chain
        val actual = assertThrows(MemoImportException::class.java) {
            runTest { repository.stageImportImages(REFERENCE) }
        }

        // Assert
        assertAll(
            { assertEquals(MemoImportFailureReason.INSUFFICIENT_STORAGE, actual.reason) },
            { assertSame(failure, actual.cause) }
        )
    }

    @ParameterizedTest
    @MethodSource("otherIOFailures")
    fun boundaryStageMapsIOExceptionWithoutOutOfSpaceToInvalidArchive(failure: IOException) {
        // Arrange
        val repository = failingStageRepository(failure)

        // Act
        // Boundary: an I/O failure without ENOSPC is treated as an unreadable archive
        val actual = assertThrows(MemoImportException::class.java) {
            runTest { repository.stageImportImages(REFERENCE) }
        }

        // Assert
        assertAll(
            { assertEquals(MemoImportFailureReason.INVALID_ARCHIVE, actual.reason) },
            { assertSame(failure, actual.cause) }
        )
    }

    @Test
    fun errorStagePropagatesUnexpectedFailureUnchanged() {
        // Arrange
        val failure = IllegalStateException("unexpected")
        val repository = failingStageRepository(failure)

        // Act
        // Error: failures outside archive and I/O errors are not disguised as import errors
        val actual = assertThrows(IllegalStateException::class.java) {
            runTest { repository.stageImportImages(REFERENCE) }
        }

        // Assert
        assertAll(
            { assertSame(failure, actual) },
            { assertEquals(0, actual.suppressed.size) }
        )
    }

    @Test
    fun interactionStageFailureRollsBackAndClosesSession() {
        // Arrange
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val repository = failingStageRepository(
            failure = IOException("Unexpected end of stream"),
            sessionDataSource = sessionDataSource
        )

        // Act
        // Interaction: a failed staging closes its session after a clean rollback
        val actual = assertThrows(MemoImportException::class.java) {
            runTest { repository.stageImportImages(REFERENCE) }
        }

        // Assert
        assertEquals(0, actual.suppressed.size)
        coVerify(exactly = 1) { sessionDataSource.close(TOKEN) }
    }

    @Test
    fun boundaryRollbackFailureSameAsStageFailureIsNotSuppressedIntoItself() {
        // Arrange
        // Coroutine stack trace recovery would otherwise rethrow a copy across withContext
        val failure = NonCopyableFailure("storage is broken")
        val extractor = mockk<MemoImportArchiveExtractor>()
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery { sessionDataSource.open() } returns TOKEN
        every { extractor.extractImages(REFERENCE, TOKEN) } throws failure
        coEvery {
            imageFileDataSource.listImageFileNamesStartingWith("${TOKEN.value}-")
        } throws failure
        val repository = stagingRepository(extractor, sessionDataSource, imageFileDataSource)

        // Act
        // Boundary: the same instance thrown again by rollback is not added to itself
        val actual = assertThrows(NonCopyableFailure::class.java) {
            runTest { repository.stageImportImages(REFERENCE) }
        }

        // Assert
        assertAll(
            { assertSame(failure, actual) },
            { assertEquals(0, actual.suppressed.size) }
        )
    }

    @Test
    fun normalStageNumbersImagesAcrossMemosInManifestOrder() = runTest {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 16, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(id = "memo-1", images = listOf(imageDtoFixture(content, index = 1))),
                memoDtoFixture(
                    id = "memo-2",
                    images = listOf(
                        imageDtoFixture(content, index = 2),
                        imageDtoFixture(content, index = 3)
                    )
                )
            )
        )
        val movedFileNames = mutableListOf<String>()
        val repository = succeedingStageRepository(manifest, movedFileNames)

        // Act
        // Normal: images are numbered across memos in the order the manifest lists them
        val staged = repository.stageImportImages(REFERENCE)

        // Assert
        val expected = listOf(
            "session-1-00000001.jpg",
            "session-1-00000002.jpg",
            "session-1-00000003.jpg"
        )
        assertAll(
            { assertEquals(expected, movedFileNames) },
            {
                assertEquals(
                    expected,
                    staged.data.memos.flatMap { memo -> memo.images.map { it.fileName.value } }
                )
            }
        )
    }

    @ParameterizedTest
    @MethodSource("importedExtensions")
    fun boundaryStageDerivesStoredExtensionFromOriginalFileName(
        originalFileName: String,
        expectedExtension: String
    ) = runTest {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 16, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(imageDtoFixture(content, fileName = originalFileName))
                )
            )
        )
        val movedFileNames = mutableListOf<String>()
        val repository = succeedingStageRepository(manifest, movedFileNames)

        // Act
        // Boundary: the extension is lowercased, stripped to letters and digits, and capped
        repository.stageImportImages(REFERENCE)

        // Assert
        assertEquals(listOf("session-1-00000001.$expectedExtension"), movedFileNames)
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

    private fun stagingRepository(
        extractor: MemoImportArchiveExtractor,
        sessionDataSource: MemoImportSessionDataSource,
        imageFileDataSource: MemoImageFileDataSource
    ) = StagingMemoImportArchiveRepository(
        extractor = extractor,
        sessionDataSource = sessionDataSource,
        imageFileDataSource = imageFileDataSource,
        memoDao = mockk<MemoDao>(),
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun failingStageRepository(
        failure: Throwable,
        sessionDataSource: MemoImportSessionDataSource = mockk()
    ): StagingMemoImportArchiveRepository {
        val extractor = mockk<MemoImportArchiveExtractor>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery { sessionDataSource.open() } returns TOKEN
        coEvery { sessionDataSource.close(TOKEN) } returns Unit
        every { extractor.extractImages(REFERENCE, TOKEN) } throws failure
        coEvery { imageFileDataSource.listImageFileNamesStartingWith(any()) } returns emptyList()
        return stagingRepository(extractor, sessionDataSource, imageFileDataSource)
    }

    private fun succeedingStageRepository(
        manifest: LiteMemoExportDto,
        movedFileNames: MutableList<String>
    ): StagingMemoImportArchiveRepository {
        val extractor = mockk<MemoImportArchiveExtractor>()
        val sessionDataSource = mockk<MemoImportSessionDataSource>()
        val imageFileDataSource = mockk<MemoImageFileDataSource>()
        coEvery { sessionDataSource.open() } returns TOKEN
        every { extractor.extractImages(REFERENCE, TOKEN) } returns manifest
        every { sessionDataSource.stagedImageFile(TOKEN, any()) } answers {
            File(secondArg<String>())
        }
        coEvery { imageFileDataSource.moveIntoImages(any(), capture(movedFileNames)) } returns Unit
        return stagingRepository(extractor, sessionDataSource, imageFileDataSource)
    }

    private class NonCopyableFailure(message: String) :
        IllegalStateException(message),
        CopyableThrowable<NonCopyableFailure> {

        override fun createCopy(): NonCopyableFailure? = null
    }

    private companion object {
        val REFERENCE = ExportFileReference("content://import.zip")
        val TOKEN = MemoImportSessionToken("session-1")

        val EXPECTED_IMPORT_REASONS = mapOf(
            MemoArchiveFailureReason.UNSUPPORTED_VERSION to
                MemoImportFailureReason.UNSUPPORTED_VERSION,
            MemoArchiveFailureReason.INVALID_SIGNATURE to MemoImportFailureReason.INVALID_ARCHIVE,
            MemoArchiveFailureReason.MALFORMED_ARCHIVE to MemoImportFailureReason.INVALID_ARCHIVE,
            MemoArchiveFailureReason.INVALID_ENTRY_NAME to MemoImportFailureReason.INVALID_ARCHIVE,
            MemoArchiveFailureReason.DUPLICATE_IDENTIFIER to
                MemoImportFailureReason.INVALID_ARCHIVE,
            MemoArchiveFailureReason.LIMIT_EXCEEDED to MemoImportFailureReason.SIZE_LIMIT_EXCEEDED,
            MemoArchiveFailureReason.SIZE_MISMATCH to MemoImportFailureReason.INVALID_IMAGE,
            MemoArchiveFailureReason.CHECKSUM_MISMATCH to MemoImportFailureReason.INVALID_IMAGE
        )

        @JvmStatic
        fun archiveFailureReasons(): List<String> = MemoArchiveFailureReason.entries.map { it.name }

        @JvmStatic
        fun outOfSpaceFailures(): List<IOException> = listOf(
            IOException("write failed: ENOSPC"),
            IOException("No space left on device"),
            IOException(
                "copy failed",
                IOException("write failed", RuntimeException("ENOSPC (No space left on device)"))
            )
        )

        @JvmStatic
        fun otherIOFailures(): List<IOException> = listOf(
            IOException(),
            IOException("Unexpected end of stream"),
            IOException("copy failed", IOException("Permission denied"))
        )

        @JvmStatic
        fun importedExtensions(): List<Arguments> = listOf(
            Arguments.of("photo", "img"),
            Arguments.of("photo.", "img"),
            Arguments.of("photo.!!!", "img"),
            Arguments.of("PHOTO.JPG", "jpg"),
            Arguments.of("archive.tar.gz", "gz"),
            Arguments.of("photo.j-p_g", "jpg"),
            Arguments.of("photo.abcdefgh", "abcdefgh"),
            Arguments.of("photo.abcdefghi", "abcdefgh")
        )
    }

}
