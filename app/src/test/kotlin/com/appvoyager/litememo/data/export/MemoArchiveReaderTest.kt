package com.appvoyager.litememo.data.export

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MemoArchiveReaderTest {

    @Test
    fun normalReadRestoresManifestAndImageOrder() {
        // Arrange
        val first = compressedImageBytes(sizeBytes = 1024, seed = 1)
        val second = compressedImageBytes(sizeBytes = 2048, seed = 2)
        val manifest = manifestFixture(
            tags = listOf(tagDtoFixture()),
            memos = listOf(
                memoDtoFixture(
                    isFavorite = true,
                    tagIds = listOf("tag-1"),
                    images = listOf(
                        imageDtoFixture(first, index = 1),
                        imageDtoFixture(second, index = 2)
                    )
                )
            )
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(first, second)))

        // Act
        // Normal: manifest contents and image order survive the round trip
        val result = readArchive(archive)

        // Assert
        assertEquals(manifest, result.manifest)
    }

    @Test
    fun normalReadRestoresImageBytesForEveryEntry() {
        // Arrange
        val first = compressedImageBytes(sizeBytes = 1024, seed = 1)
        val second = compressedImageBytes(sizeBytes = 2048, seed = 2)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(
                        imageDtoFixture(first, index = 1),
                        imageDtoFixture(second, index = 2)
                    )
                )
            )
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(first, second)))

        // Act
        // Normal: every image entry is restored as the declared byte sequence
        val restored = readArchive(archive).images.mapValues { it.value.toList() }

        // Assert
        assertEquals(
            mapOf(
                MemoArchiveLayout.imageEntryName(1) to first.toList(),
                MemoArchiveLayout.imageEntryName(2) to second.toList()
            ),
            restored
        )
    }

    @Test
    fun boundaryReadAcceptsImageOnlyMemo() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 512, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(title = "", body = "", images = listOf(imageDtoFixture(content)))
            )
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(content)))

        // Act
        // Boundary: a memo with empty title and body but images is readable
        val result = readArchive(archive)

        // Assert
        assertEquals(manifest, result.manifest)
    }

    @Test
    fun errorReadRejectsInputWithoutZipSignature() {
        // Arrange
        val notAnArchive = """{"version":1}""".toByteArray(Charsets.UTF_8)

        // Act & Assert
        // Error: non-ZIP input such as standalone JSON is rejected by its leading bytes
        assertArchiveFailure(MemoArchiveFailureReason.INVALID_SIGNATURE) {
            readArchive(notAnArchive)
        }
    }

    @Test
    fun errorReadRejectsArchiveWhoseFirstEntryIsNotManifest() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 128, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.imageEntryName(1) to content,
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest)
            )
        )

        // Act & Assert
        // Error: an archive whose image entry precedes the manifest is rejected
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsArchiveWithSecondManifestEntry() {
        // Arrange
        val manifest = manifestFixture(memos = listOf(memoDtoFixture()))
        val single = rawArchive(
            listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest))
        )
        val archive = concatArchives(single, single)

        // Act & Assert
        // Error: manifest.json must appear exactly once at the archive root
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsEntryTheManifestDoesNotDeclare() {
        // Arrange
        val manifest = manifestFixture(memos = listOf(memoDtoFixture()))
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest),
                "images/00000001" to compressedImageBytes(sizeBytes = 32, seed = 1)
            )
        )

        // Act & Assert
        // Error: file entries missing from the manifest are not tolerated
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsArchiveMissingDeclaredImageEntry() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 128, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = rawArchive(
            listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest))
        )

        // Act & Assert
        // Error: an archive missing an image entry the manifest references is rejected
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsEntryNameThatEscapesTheArchive() {
        // Arrange
        val manifest = manifestFixture(memos = listOf(memoDtoFixture()))
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest),
                "../evil.txt" to compressedImageBytes(sizeBytes = 32, seed = 1)
            )
        )

        // Act & Assert
        // Error: entry names that would cause zip slip are never extracted
        assertArchiveFailure(MemoArchiveFailureReason.INVALID_ENTRY_NAME) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsEntryLargerThanDeclaredSize() {
        // Arrange
        val declared = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(declared))))
        )
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest),
                MemoArchiveLayout.imageEntryName(1) to ByteArray(EXPANDED_ENTRY_BYTES)
            )
        )

        // Act & Assert
        // Error: an entry expanding beyond its declared size is cut off before it is read out
        assertArchiveFailure(MemoArchiveFailureReason.SIZE_MISMATCH) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsEntryThatDoesNotMatchDeclaredChecksum() {
        // Arrange
        val declared = compressedImageBytes(sizeBytes = 256, seed = 1)
        val actual = compressedImageBytes(sizeBytes = 256, seed = 2)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(declared))))
        )
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest),
                MemoArchiveLayout.imageEntryName(1) to actual
            )
        )

        // Act & Assert
        // Error: an image whose SHA-256 differs from the manifest is rejected
        assertArchiveFailure(MemoArchiveFailureReason.CHECKSUM_MISMATCH) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsUnsupportedVersion() {
        // Arrange
        val manifest = manifestFixture(version = MemoArchiveLayout.VERSION + 1)
        val archive = rawArchive(
            listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest))
        )

        // Act & Assert
        // Error: an unsupported version rejects the archive without extracting images
        assertArchiveFailure(MemoArchiveFailureReason.UNSUPPORTED_VERSION) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsManifestThatIsNotValidJson() {
        // Arrange
        val manifestBytes = "not a manifest".toByteArray(Charsets.UTF_8)
        val archive = rawArchive(
            listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME to manifestBytes)
        )

        // Act & Assert
        // Error: an archive whose manifest is not parseable JSON is rejected
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(archive)
        }
    }

    @Test
    fun errorReadRejectsTruncatedArchive() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 4096, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(content)))
        val truncated = archive.copyOf(archive.size - TRUNCATED_TAIL_BYTES)

        // Act & Assert
        // Error: a truncated archive is never partially accepted
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(truncated)
        }
    }

    @Test
    fun boundaryReadRejectsArchiveOverArchiveSizeLimitWithoutDeclaredSize() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 4096, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(content)))
        val limits = MemoArchiveLimits.DEFAULT.copy(maxArchiveBytes = 1024)

        // Act & Assert
        // Boundary: the limit is enforced by bytes actually read even when size is unknown upfront
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            readArchive(archive, limits)
        }
    }

    @Test
    fun boundaryReadRejectsManifestOverManifestSizeLimit() {
        // Arrange
        val manifest = manifestFixture(
            memos = List(32) { index -> memoDtoFixture(id = "memo-$index") }
        )
        val archive = rawArchive(
            listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest))
        )
        val limits = MemoArchiveLimits.DEFAULT.copy(maxManifestBytes = 64)

        // Act & Assert
        // Boundary: the limit also applies to how far the manifest expands
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            readArchive(archive, limits)
        }
    }

    @Test
    fun errorReadDoesNotOpenSinkWhenEntryNameIsUnsafe() {
        // Arrange
        val manifest = manifestFixture(memos = listOf(memoDtoFixture()))
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest),
                "../evil.txt" to compressedImageBytes(sizeBytes = 32, seed = 1)
            )
        )
        val openedEntries = mutableListOf<String>()

        // Act
        // Error/Interaction: a failed archive never opens any extraction target
        assertThrows(MemoArchiveException::class.java) {
            MemoArchiveReader(archiveJson, MemoArchiveLimits.DEFAULT)
                .read(ByteArrayInputStream(archive)) { metadata ->
                    openedEntries += metadata.archiveEntry
                    ByteArrayOutputStream()
                }
        }

        // Assert
        assertTrue(openedEntries.isEmpty())
    }

    private fun contentsOf(
        manifest: LiteMemoExportDto,
        contents: List<ByteArray>
    ): Map<String, ByteArray> = manifest.memos
        .flatMap { it.images }
        .mapIndexed { index, image -> image.archiveEntry to contents[index] }
        .toMap()
}

private const val EXPANDED_ENTRY_BYTES = 1024 * 1024

private const val TRUNCATED_TAIL_BYTES = 512
