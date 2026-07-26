package com.appvoyager.litememo.data.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class MemoArchiveWriterTest {

    @Test
    fun normalWritePlacesManifestAsFirstEntry() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 2048, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )

        // Act
        // Normal: entries are ordered so import settles the manifest before extracting images
        val archive = writeArchive(manifest, mapOf(MemoArchiveLayout.imageEntryName(1) to content))

        // Assert
        assertEquals(
            listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME, MemoArchiveLayout.imageEntryName(1)),
            entryNames(archive)
        )
    }

    @Test
    fun normalWriteKeepsAlreadyCompressedImageBytesIntact() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 4096, seed = 3)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = writeArchive(manifest, mapOf(MemoArchiveLayout.imageEntryName(1) to content))

        // Act
        // Normal: already-compressed images are restored byte for byte
        val restored = readArchive(archive).images.getValue(MemoArchiveLayout.imageEntryName(1))

        // Assert
        assertEquals(content.toList(), restored.toList())
    }

    @Test
    fun errorWriteRejectsImageWhoseBytesDoNotMatchDeclaredChecksum() {
        // Arrange
        val declared = compressedImageBytes(sizeBytes = 512, seed = 1)
        val actual = compressedImageBytes(sizeBytes = 512, seed = 2)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(declared))))
        )

        // Act & Assert
        // Error: an image swapped after metadata collection leaves no broken archive
        assertArchiveFailure(MemoArchiveFailureReason.CHECKSUM_MISMATCH) {
            writeArchive(manifest, mapOf(MemoArchiveLayout.imageEntryName(1) to actual))
        }
    }

    @Test
    fun errorWriteRejectsImageLargerThanDeclaredSize() {
        // Arrange
        val declared = compressedImageBytes(sizeBytes = 512, seed = 1)
        val larger = declared + compressedImageBytes(sizeBytes = 16, seed = 2)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(declared))))
        )

        // Act & Assert
        // Error: an image larger than its declared size aborts the write
        assertArchiveFailure(MemoArchiveFailureReason.SIZE_MISMATCH) {
            writeArchive(manifest, mapOf(MemoArchiveLayout.imageEntryName(1) to larger))
        }
    }

    @Test
    fun errorWriteRejectsManifestThatFailsStructureValidation() {
        // Arrange
        val manifest = manifestFixture(version = MemoArchiveLayout.VERSION + 1)

        // Act & Assert
        // Error: the writer runs the same manifest validation as the reader
        assertArchiveFailure(MemoArchiveFailureReason.UNSUPPORTED_VERSION) {
            writeArchive(manifest)
        }
    }

    @Test
    fun boundaryWriteRejectsManifestOverManifestSizeLimit() {
        // Arrange
        val manifest = manifestFixture(
            memos = List(32) { index -> memoDtoFixture(id = "memo-$index") }
        )
        val limits = MemoArchiveLimits.DEFAULT.copy(maxManifestBytes = 64)

        // Act & Assert
        // Boundary: a manifest alone above the limit writes no archive
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            writeArchive(manifest, limits = limits)
        }
    }

    private fun entryNames(archive: ByteArray): List<String> = buildList {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                add(entry.name)
            }
        }
    }
}
