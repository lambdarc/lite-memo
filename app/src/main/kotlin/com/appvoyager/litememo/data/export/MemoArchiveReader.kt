package com.appvoyager.litememo.data.export

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

internal class MemoArchiveReader(private val json: Json, private val limits: MemoArchiveLimits) {

    fun read(
        source: InputStream,
        openImageStream: (MemoImageExportDto) -> OutputStream
    ): LiteMemoExportDto {
        val limited = ArchiveSizeLimitedInputStream(source, limits.maxArchiveBytes)
        return try {
            ZipInputStream(requireZipSignature(limited)).use { zip ->
                readEntries(zip, openImageStream)
            }
        } catch (e: ZipException) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive is not a readable ZIP.",
                e
            )
        } catch (e: EOFException) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive ended before all declared entries were read.",
                e
            )
        }
    }

    private fun readEntries(
        zip: ZipInputStream,
        openImageStream: (MemoImageExportDto) -> OutputStream
    ): LiteMemoExportDto {
        val manifest = readManifest(zip)
        val pendingImages = linkedMapOf<String, MemoImageExportDto>()
        manifest.memos.flatMap { it.images }.forEach { pendingImages[it.archiveEntry] = it }

        var entryCount = 1
        while (true) {
            val entry = zip.nextEntry ?: break

            entryCount++
            requireEntryCount(entryCount)
            requireSafeEntryName(entry.name)
            val metadata = pendingImages.remove(entry.name)
            if (entry.isDirectory || metadata == null) {
                archiveFailure(
                    MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                    "Archive contains an entry that the manifest does not declare."
                )
            }
            openImageStream(metadata).use { sink ->
                MemoArchiveImageCopier.copyVerified(zip, sink, metadata)
            }
        }

        if (pendingImages.isNotEmpty()) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive is missing ${pendingImages.size} image entries declared by the manifest."
            )
        }
        return manifest
    }

    private fun readManifest(zip: ZipInputStream): LiteMemoExportDto {
        val entry = zip.nextEntry ?: archiveFailure(
            MemoArchiveFailureReason.MALFORMED_ARCHIVE,
            "Archive contains no entries."
        )
        requireSafeEntryName(entry.name)
        if (entry.name != MemoArchiveLayout.MANIFEST_ENTRY_NAME) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive must start with ${MemoArchiveLayout.MANIFEST_ENTRY_NAME}."
            )
        }

        val manifest = try {
            json.decodeFromString<LiteMemoExportDto>(
                readManifestBytes(zip).toString(Charsets.UTF_8)
            )
        } catch (e: SerializationException) {
            archiveFailure(
                MemoArchiveFailureReason.MALFORMED_ARCHIVE,
                "Archive manifest is not valid JSON.",
                e
            )
        }
        MemoArchiveManifestValidator.validate(manifest, limits)
        return manifest
    }

    private fun readManifestBytes(zip: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0L

        while (true) {
            val read = zip.read(buffer)
            if (read < 0) break

            totalBytes += read
            if (totalBytes > limits.maxManifestBytes) {
                archiveFailure(
                    MemoArchiveFailureReason.LIMIT_EXCEEDED,
                    "Archive manifest is larger than ${limits.maxManifestBytes} bytes."
                )
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun requireZipSignature(source: InputStream): InputStream {
        val pushback = PushbackInputStream(source, MemoArchiveSignature.LENGTH)
        if (!MemoArchiveSignature.matches(pushback)) {
            archiveFailure(
                MemoArchiveFailureReason.INVALID_SIGNATURE,
                "Input does not start with a ZIP local file header."
            )
        }
        return pushback
    }

    private fun requireEntryCount(entryCount: Int) {
        if (entryCount > limits.maxEntryCount) {
            archiveFailure(
                MemoArchiveFailureReason.LIMIT_EXCEEDED,
                "Archive contains more than ${limits.maxEntryCount} entries."
            )
        }
    }

    private fun requireSafeEntryName(name: String) {
        if (!MemoArchiveLayout.isSafeEntryName(name)) {
            archiveFailure(
                MemoArchiveFailureReason.INVALID_ENTRY_NAME,
                "Archive contains an entry name that escapes the archive layout."
            )
        }
    }

    private class ArchiveSizeLimitedInputStream(source: InputStream, private val limit: Long) :
        FilterInputStream(source) {

        private var totalBytes = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) countBytes(1)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = super.read(b, off, len)
            if (read > 0) countBytes(read.toLong())
            return read
        }

        private fun countBytes(count: Long) {
            totalBytes += count
            if (totalBytes > limit) {
                archiveFailure(
                    MemoArchiveFailureReason.LIMIT_EXCEEDED,
                    "Archive is larger than $limit bytes."
                )
            }
        }

    }

}
