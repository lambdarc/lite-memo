package com.appvoyager.litememo.data.repository

import android.content.Context
import androidx.core.net.toUri
import com.appvoyager.litememo.data.di.IoDispatcher
import com.appvoyager.litememo.data.export.MemoArchiveFailureReason
import com.appvoyager.litememo.data.export.MemoArchiveLayout
import com.appvoyager.litememo.data.export.MemoArchiveLimits
import com.appvoyager.litememo.data.export.MemoArchiveReader
import com.appvoyager.litememo.data.export.MemoArchiveWriter
import com.appvoyager.litememo.data.export.MemoExportSessionDataSource
import com.appvoyager.litememo.data.export.archiveFailure
import com.appvoyager.litememo.data.image.MemoImageFileDataSource
import com.appvoyager.litememo.data.mapper.toExportDto
import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import com.appvoyager.litememo.di.ArchiveLimits
import com.appvoyager.litememo.di.ExportJson
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoExportToken
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject

class FileSystemMemoExportArchiveRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ExportJson private val json: Json,
    @param:ArchiveLimits private val limits: MemoArchiveLimits,
    private val sessionDataSource: MemoExportSessionDataSource,
    private val imageFileDataSource: MemoImageFileDataSource,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MemoExportArchiveRepository {

    override suspend fun prepare(data: ExportData): MemoExportToken = withContext(ioDispatcher) {
        val token = sessionDataSource.open()
        val prepared = runCatching {
            prepareArchive(data, token)
            token
        }
        val failure = prepared.exceptionOrNull()
        if (failure != null) {
            val cleanupFailure = runCatching {
                withContext(NonCancellable) { sessionDataSource.discard(token) }
            }.exceptionOrNull()
            if (cleanupFailure != null && cleanupFailure !== failure) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
        prepared.getOrThrow()
    }

    override suspend fun write(token: MemoExportToken, destination: ExportFileReference) {
        withContext(ioDispatcher) {
            val source = sessionDataSource.preparedFile(token)
            if (!source.isFile) throw IOException("Prepared export archive is unavailable.")
            context.contentResolver.openOutputStream(
                destination.value.toUri(),
                "wt"
            )?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("Failed to open the selected export destination.")
        }
    }

    override suspend fun discard(token: MemoExportToken) {
        sessionDataSource.discard(token)
    }

    override suspend fun deleteAbandonedPreparedExports() {
        val failures = mutableListOf<Throwable>()
        sessionDataSource.claimAbandonedTokens().forEach { token ->
            val failure = runCatching { sessionDataSource.discard(token) }.exceptionOrNull()
                ?: return@forEach
            if (failure is CancellationException) throw failure
            failures += failure
        }
        failures.firstOrNull()?.let { primary ->
            failures.drop(1).filterNot { it === primary }.forEach(primary::addSuppressed)
            throw primary
        }
    }

    private suspend fun prepareArchive(data: ExportData, token: MemoExportToken) {
        val imagesById = linkedMapOf<String, java.io.File>()
        var sequence = 0
        var totalImageBytes = 0L
        val memoDtos = data.memos.map { memo ->
            val imageDtos = memo.images.map { image ->
                sequence++
                val file = imageFileDataSource.imageFile(image.fileName.value)
                val metadata = imageMetadata(
                    file = file,
                    id = image.id.value,
                    fileName = image.fileName.value,
                    archiveEntry = MemoArchiveLayout.imageEntryName(sequence)
                )
                totalImageBytes += metadata.sizeBytes
                if (totalImageBytes > limits.maxTotalImageBytes) {
                    archiveFailure(
                        MemoArchiveFailureReason.LIMIT_EXCEEDED,
                        "Export images exceed the archive size limit."
                    )
                }
                imagesById[image.id.value] = file
                metadata
            }
            memo.toExportDto(imageDtos)
        }
        val manifest = LiteMemoExportDto(
            version = data.version,
            exportedAt = data.exportedAt.value,
            tags = data.tags.map { it.toExportDto() },
            memos = memoDtos
        )
        val partial = sessionDataSource.partialFile(token)
        partial.outputStream().use { output ->
            MemoArchiveWriter(json, limits).write(output, manifest) { metadata ->
                imagesById[metadata.id]?.inputStream()
                    ?: throw IOException("Export image metadata is inconsistent.")
            }
        }
        verifyArchive(partial, manifest)
        sessionDataSource.publish(token)
    }

    private fun imageMetadata(
        file: java.io.File,
        id: String,
        fileName: String,
        archiveEntry: String
    ): MemoImageExportDto {
        if (!file.isFile || !file.canRead()) throw IOException("An export image is unavailable.")
        val digest = MessageDigest.getInstance("SHA-256")
        var sizeBytes = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                sizeBytes += read
                if (sizeBytes > limits.maxImageBytes) {
                    archiveFailure(
                        MemoArchiveFailureReason.LIMIT_EXCEEDED,
                        "An export image exceeds the archive size limit."
                    )
                }
                digest.update(buffer, 0, read)
            }
        }
        return MemoImageExportDto(
            id = id,
            fileName = fileName,
            archiveEntry = archiveEntry,
            sizeBytes = sizeBytes,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    private fun verifyArchive(file: java.io.File, expected: LiteMemoExportDto) {
        val actual = file.inputStream().use { input ->
            MemoArchiveReader(json, limits).read(input) { DiscardingOutputStream }
        }
        if (actual != expected) throw IOException("Prepared export archive verification failed.")
    }

    private object DiscardingOutputStream : OutputStream() {
        override fun write(value: Int) = Unit
        override fun write(buffer: ByteArray, offset: Int, length: Int) = Unit
    }

}
