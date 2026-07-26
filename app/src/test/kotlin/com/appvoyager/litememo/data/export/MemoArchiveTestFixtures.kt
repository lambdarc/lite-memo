package com.appvoyager.litememo.data.export

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import com.appvoyager.litememo.data.model.export.TagExportDto
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

internal val archiveJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte) }

internal fun compressedImageBytes(sizeBytes: Int, seed: Int): ByteArray =
    Random(seed).nextBytes(sizeBytes)

internal fun tagDtoFixture(id: String = "tag-1", name: String = "Work") = TagExportDto(
    id = id,
    name = name,
    colorArgb = 0xFF6750A4,
    createdAt = 1000L
)

internal fun memoDtoFixture(
    id: String = "memo-1",
    title: String = "Title",
    body: String = "Body",
    createdAt: Long = 1000L,
    updatedAt: Long = 2000L,
    isFavorite: Boolean = false,
    tagIds: List<String> = emptyList(),
    images: List<MemoImageExportDto> = emptyList()
) = MemoExportDto(
    id = id,
    title = title,
    body = body,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isFavorite = isFavorite,
    tagIds = tagIds,
    images = images
)

internal fun imageDtoFixture(
    content: ByteArray,
    index: Int = 1,
    id: String = "image-$index",
    fileName: String = "picked-$index.jpg",
    archiveEntry: String = MemoArchiveLayout.imageEntryName(index),
    sizeBytes: Long = content.size.toLong(),
    sha256: String = sha256Hex(content)
) = MemoImageExportDto(
    id = id,
    fileName = fileName,
    archiveEntry = archiveEntry,
    sizeBytes = sizeBytes,
    sha256 = sha256
)

internal fun manifestFixture(
    version: Int = MemoArchiveLayout.VERSION,
    exportedAt: Long = 5000L,
    tags: List<TagExportDto> = emptyList(),
    memos: List<MemoExportDto> = emptyList()
) = LiteMemoExportDto(version = version, exportedAt = exportedAt, tags = tags, memos = memos)

internal fun encodeManifest(manifest: LiteMemoExportDto): ByteArray =
    archiveJson.encodeToString(manifest).toByteArray(Charsets.UTF_8)

internal fun writeArchive(
    manifest: LiteMemoExportDto,
    contents: Map<String, ByteArray> = emptyMap(),
    limits: MemoArchiveLimits = MemoArchiveLimits.DEFAULT
): ByteArray {
    val output = ByteArrayOutputStream()
    MemoArchiveWriter(archiveJson, limits).write(output, manifest) { metadata ->
        ByteArrayInputStream(requireNotNull(contents[metadata.archiveEntry]))
    }
    return output.toByteArray()
}

internal fun rawArchive(entries: List<Pair<String, ByteArray>>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}

internal fun concatArchives(head: ByteArray, tail: ByteArray): ByteArray =
    head.copyOf(centralDirectoryOffset(head)) + tail

private fun centralDirectoryOffset(archive: ByteArray): Int {
    val endOfCentralDirectory = archive.size - END_OF_CENTRAL_DIRECTORY_LENGTH
    return ByteBuffer
        .wrap(archive, endOfCentralDirectory + CENTRAL_DIRECTORY_OFFSET_FIELD, Int.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int
}

internal fun readArchive(
    archive: ByteArray,
    limits: MemoArchiveLimits = MemoArchiveLimits.DEFAULT
): ArchiveReadResult {
    val sinks = linkedMapOf<String, ByteArrayOutputStream>()
    val manifest = MemoArchiveReader(archiveJson, limits)
        .read(ByteArrayInputStream(archive)) { metadata ->
            ByteArrayOutputStream().also { sinks[metadata.archiveEntry] = it }
        }
    return ArchiveReadResult(manifest, sinks.mapValues { it.value.toByteArray() })
}

internal fun assertArchiveFailure(reason: MemoArchiveFailureReason, block: () -> Unit) {
    val exception = assertThrows(MemoArchiveException::class.java) { block() }
    assertEquals(reason, exception.reason)
}

internal class ArchiveReadResult(
    val manifest: LiteMemoExportDto,
    val images: Map<String, ByteArray>
)

private const val END_OF_CENTRAL_DIRECTORY_LENGTH = 22
private const val CENTRAL_DIRECTORY_OFFSET_FIELD = 16
