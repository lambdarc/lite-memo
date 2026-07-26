package com.appvoyager.litememo.data.export

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class MemoArchiveWriter(private val json: Json, private val limits: MemoArchiveLimits) {

    fun write(
        target: OutputStream,
        manifest: LiteMemoExportDto,
        openImageStream: (MemoImageExportDto) -> InputStream
    ) {
        MemoArchiveManifestValidator.validate(manifest, limits)
        val manifestBytes = encodeManifest(manifest)

        ZipOutputStream(target).use { zip ->
            zip.putNextEntry(ZipEntry(MemoArchiveLayout.MANIFEST_ENTRY_NAME))
            zip.write(manifestBytes)
            zip.closeEntry()

            zip.setLevel(Deflater.NO_COMPRESSION)
            manifest.memos.flatMap { it.images }.forEach { metadata ->
                zip.putNextEntry(ZipEntry(metadata.archiveEntry))
                openImageStream(metadata).use { source ->
                    MemoArchiveImageCopier.copyVerified(source, zip, metadata)
                }
                zip.closeEntry()
            }
        }
    }

    private fun encodeManifest(manifest: LiteMemoExportDto): ByteArray {
        val bytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        if (bytes.size > limits.maxManifestBytes) {
            archiveFailure(
                MemoArchiveFailureReason.LIMIT_EXCEEDED,
                "Archive manifest is larger than ${limits.maxManifestBytes} bytes."
            )
        }
        return bytes
    }

}
