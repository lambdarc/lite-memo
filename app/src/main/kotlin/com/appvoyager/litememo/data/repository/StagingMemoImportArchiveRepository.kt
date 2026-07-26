package com.appvoyager.litememo.data.repository

import com.appvoyager.litememo.data.di.IoDispatcher
import com.appvoyager.litememo.data.export.MemoArchiveException
import com.appvoyager.litememo.data.export.MemoArchiveFailureReason
import com.appvoyager.litememo.data.export.MemoImportArchiveExtractor
import com.appvoyager.litememo.data.export.MemoImportSessionDataSource
import com.appvoyager.litememo.data.image.MemoImageFileDataSource
import com.appvoyager.litememo.data.local.dao.MemoDao
import com.appvoyager.litememo.data.mapper.toDomain
import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.model.StagedMemoImport
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoImageFileName
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken
import com.appvoyager.litememo.domain.repository.MemoImportArchiveRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

class StagingMemoImportArchiveRepository @Inject constructor(
    private val extractor: MemoImportArchiveExtractor,
    private val sessionDataSource: MemoImportSessionDataSource,
    private val imageFileDataSource: MemoImageFileDataSource,
    private val memoDao: MemoDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MemoImportArchiveRepository {

    override suspend fun stageImportImages(reference: ExportFileReference): StagedMemoImport =
        withContext(ioDispatcher) {
            val token = sessionDataSource.open()
            val staged = runCatching { stage(reference, token) }
            val failure = staged.exceptionOrNull()
            if (failure != null) {
                val importFailure = failure.asImportFailure()
                val rollbackFailure = runCatching {
                    withContext(NonCancellable) { rollbackStagedImport(token) }
                }.exceptionOrNull()
                if (rollbackFailure != null && rollbackFailure !== importFailure) {
                    importFailure.addSuppressed(rollbackFailure)
                }
                throw importFailure
            }
            staged.getOrThrow()
        }

    override suspend fun completeStagedImport(token: MemoImportSessionToken) {
        sessionDataSource.close(token)
    }

    override suspend fun rollbackStagedImport(token: MemoImportSessionToken) {
        withContext(ioDispatcher) {
            deleteUnreferencedImagesOf(token)
            sessionDataSource.close(token)
        }
    }

    override suspend fun deleteUnreferencedImportImages() {
        withContext(ioDispatcher) {
            val failures = mutableListOf<Throwable>()
            sessionDataSource.claimAbandonedTokens().forEach { token ->
                val failure = runCatching {
                    deleteUnreferencedImagesOf(token)
                    sessionDataSource.close(token)
                }.exceptionOrNull() ?: return@forEach
                if (failure is CancellationException) throw failure
                failures += failure
            }
            failures.firstOrNull()?.let { primaryFailure ->
                failures.drop(1)
                    .filterNot { it === primaryFailure }
                    .forEach(primaryFailure::addSuppressed)
                throw primaryFailure
            }
        }
    }

    private suspend fun stage(
        reference: ExportFileReference,
        token: MemoImportSessionToken
    ): StagedMemoImport {
        val manifest = extractor.extractImages(reference, token)
        return StagedMemoImport(
            token = token,
            data = manifest.toDomain(moveImagesIntoStore(manifest, token))
        )
    }

    private suspend fun moveImagesIntoStore(
        manifest: LiteMemoExportDto,
        token: MemoImportSessionToken
    ): Map<String, MemoImageFileName> = manifest.memos
        .flatMap { it.images }
        .mapIndexed { index, image ->
            val fileName = importedFileName(token, index + 1, image.fileName)
            imageFileDataSource.moveIntoImages(
                source = sessionDataSource.stagedImageFile(token, image.archiveEntry),
                fileName = fileName.value
            )
            image.id to fileName
        }
        .toMap()

    private suspend fun deleteUnreferencedImagesOf(token: MemoImportSessionToken) {
        val fileNames = imageFileDataSource
            .listImageFileNamesStartingWith(importedFileNamePrefix(token))
        if (fileNames.isEmpty()) return

        val referenced = fileNames.chunked(NAME_QUERY_CHUNK_SIZE)
            .flatMap { chunk -> memoDao.findReferencedImageFileNames(chunk) }
            .toSet()
        fileNames.filterNot { it in referenced }
            .forEach { imageFileDataSource.deleteImage(it) }
    }

    private fun importedFileName(
        token: MemoImportSessionToken,
        number: Int,
        originalFileName: String
    ): MemoImageFileName {
        val sequence = number.toString().padStart(IMAGE_NUMBER_LENGTH, '0')
        val extension = originalFileName.substringAfterLast('.', "")
            .lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() }
            .take(MAX_EXTENSION_LENGTH)
            .ifEmpty { FALLBACK_EXTENSION }
        return MemoImageFileName("${importedFileNamePrefix(token)}$sequence.$extension")
    }

    private fun importedFileNamePrefix(token: MemoImportSessionToken): String =
        "${token.value}$IMPORTED_FILE_NAME_SEPARATOR"

    private companion object {
        const val NAME_QUERY_CHUNK_SIZE = 900
        const val IMAGE_NUMBER_LENGTH = 8
        const val MAX_EXTENSION_LENGTH = 8
        const val FALLBACK_EXTENSION = "img"
        const val IMPORTED_FILE_NAME_SEPARATOR = "-"
    }

}

private fun Throwable.asImportFailure(): Throwable = when (this) {
    is CancellationException -> this

    is MemoArchiveException -> MemoImportException(
        reason.asImportFailureReason(),
        "The selected archive could not be imported.",
        this
    )

    is IOException -> MemoImportException(
        if (isOutOfSpace()) {
            MemoImportFailureReason.INSUFFICIENT_STORAGE
        } else {
            MemoImportFailureReason.INVALID_ARCHIVE
        },
        "The selected archive could not be read.",
        this
    )

    else -> this
}

private fun MemoArchiveFailureReason.asImportFailureReason(): MemoImportFailureReason =
    when (this) {
        MemoArchiveFailureReason.UNSUPPORTED_VERSION -> MemoImportFailureReason.UNSUPPORTED_VERSION

        MemoArchiveFailureReason.INVALID_SIGNATURE,
        MemoArchiveFailureReason.MALFORMED_ARCHIVE,
        MemoArchiveFailureReason.INVALID_ENTRY_NAME,
        MemoArchiveFailureReason.DUPLICATE_IDENTIFIER -> MemoImportFailureReason.INVALID_ARCHIVE

        MemoArchiveFailureReason.LIMIT_EXCEEDED -> MemoImportFailureReason.SIZE_LIMIT_EXCEEDED

        MemoArchiveFailureReason.SIZE_MISMATCH,
        MemoArchiveFailureReason.CHECKSUM_MISMATCH -> MemoImportFailureReason.INVALID_IMAGE
    }

private fun IOException.isOutOfSpace(): Boolean = generateSequence(this as Throwable) { it.cause }
    .any { failure ->
        val message = failure.message.orEmpty()
        message.contains(OUT_OF_SPACE_ERRNO) || message.contains(OUT_OF_SPACE_MESSAGE)
    }

private const val OUT_OF_SPACE_ERRNO = "ENOSPC"
private const val OUT_OF_SPACE_MESSAGE = "No space left on device"
