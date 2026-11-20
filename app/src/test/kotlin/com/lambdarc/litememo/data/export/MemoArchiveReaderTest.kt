package com.lambdarc.litememo.data.export

import com.lambdarc.litememo.data.model.export.LiteMemoExportDto
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.nonBlankText
import com.lambdarc.litememo.text
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

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
    fun errorReadRejectsManifestEntryNameThatIsNotValidUtf8() {
        // Arrange
        val archive = writeArchive(manifestFixture())
        val corrupted = withInvalidUtf8InEntryName(archive, MemoArchiveLayout.MANIFEST_ENTRY_NAME)

        // Act & Assert
        // Error: an undecodable first entry name is reported as a malformed archive
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(corrupted)
        }
    }

    @Test
    fun errorReadRejectsImageEntryNameThatIsNotValidUtf8() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 128, seed = 1)
        val image = imageDtoFixture(content)
        val manifest = manifestFixture(memos = listOf(memoDtoFixture(images = listOf(image))))
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(content)))
        val corrupted = withInvalidUtf8InEntryName(archive, image.archiveEntry)

        // Act & Assert
        // Error: an undecodable image entry name is reported as a malformed archive
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            readArchive(corrupted)
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
        val manifest = manifestFixture(version = ExportData.CURRENT_VERSION + 1)
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

    @Test
    fun boundaryReadAcceptsManifestAtManifestSizeLimit() {
        // Arrange
        val manifest = manifestFixture(memos = listOf(memoDtoFixture()))
        val manifestBytes = encodeManifest(manifest)
        val archive = rawArchive(listOf(MemoArchiveLayout.MANIFEST_ENTRY_NAME to manifestBytes))
        val limits = MemoArchiveLimits.DEFAULT.copy(maxManifestBytes = manifestBytes.size.toLong())

        // Act
        // Boundary: a manifest exactly at the size limit is read
        val result = readArchive(archive, limits)

        // Assert
        assertEquals(manifest, result.manifest)
    }

    @Test
    fun boundaryReadAcceptsEntryCountAtLimit() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(content)))
        val limits = MemoArchiveLimits.DEFAULT.copy(maxEntryCount = 2)

        // Act
        // Boundary: one image plus the manifest exactly fill the entry count limit
        val result = readArchive(archive, limits)

        // Assert
        assertEquals(listOf(MemoArchiveLayout.imageEntryName(1)), result.images.keys.toList())
    }

    @Test
    fun boundaryReadRejectsEntryBeyondEntryCountLimit() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(images = listOf(imageDtoFixture(content))))
        )
        val archive = rawArchive(
            listOf(
                MemoArchiveLayout.MANIFEST_ENTRY_NAME to encodeManifest(manifest),
                MemoArchiveLayout.imageEntryName(1) to content,
                MemoArchiveLayout.imageEntryName(2) to content
            )
        )
        val limits = MemoArchiveLimits.DEFAULT.copy(maxEntryCount = 2)

        // Act & Assert
        // Boundary: entries actually read are counted even when the manifest stays within the limit
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            readArchive(archive, limits)
        }
    }

    @Test
    fun propertyWrittenArchiveReadsBackToSameManifestAndImages() = runTest {
        // Act & Assert
        // Property: any valid manifest and image bytes survive a write and read round trip
        checkAll(PropTestConfig(iterations = 100), archiveSamples) { sample ->
            val archive = writeArchive(sample.manifest, sample.images)

            val result = readArchive(archive)

            assertAll(
                { assertEquals(sample.manifest, result.manifest) },
                {
                    assertEquals(
                        sample.images.mapValues { it.value.toList() },
                        result.images.mapValues { it.value.toList() }
                    )
                }
            )
        }
    }

    @Test
    fun propertyReadOfArbitraryBytesFailsOnlyWithArchiveException() = runTest {
        // Act & Assert
        // Property: random input, with or without a ZIP signature, is rejected as an archive error
        checkAll(PropTestConfig(iterations = 300), arbitraryInputs) { input ->
            assertThrows(MemoArchiveException::class.java) { readArchive(input) }
        }
    }

    @Test
    fun propertyReadOfCorruptedArchiveFailsOnlyWithArchiveException() = runTest {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 256, seed = 1)
        val manifest = manifestFixture(
            tags = listOf(tagDtoFixture()),
            memos = listOf(
                memoDtoFixture(tagIds = listOf("tag-1"), images = listOf(imageDtoFixture(content)))
            )
        )
        val archive = writeArchive(manifest, contentsOf(manifest, listOf(content)))
        val corruptions = Arb.choice(
            Arb.int(0 until archive.size).map { length -> archive.copyOf(length) },
            Arb.bind(Arb.int(archive.indices), Arb.byte()) { index, byte ->
                archive.copyOf().also { it[index] = byte }
            }
        )

        // Act & Assert
        // Property: a truncated or corrupted archive is either read or rejected as an archive error
        checkAll(PropTestConfig(iterations = 300), corruptions) { corrupted ->
            val failure = runCatching { readArchive(corrupted) }.exceptionOrNull()
            assertTrue(failure == null || failure is MemoArchiveException) {
                "unexpected failure: $failure"
            }
        }
    }

    private fun withInvalidUtf8InEntryName(archive: ByteArray, entryName: String): ByteArray {
        val nameBytes = entryName.toByteArray(Charsets.UTF_8)
        val localHeaderNameOffset = (0..archive.size - nameBytes.size).first { offset ->
            nameBytes.indices.all { archive[offset + it] == nameBytes[it] }
        }
        return archive.copyOf().also { it[localHeaderNameOffset] = INVALID_UTF8_BYTE }
    }

    private fun contentsOf(
        manifest: LiteMemoExportDto,
        contents: List<ByteArray>
    ): Map<String, ByteArray> = manifest.memos
        .flatMap { it.images }
        .mapIndexed { index, image -> image.archiveEntry to contents[index] }
        .toMap()
}

private class ArchiveSample(val manifest: LiteMemoExportDto, val images: Map<String, ByteArray>)

private val LATEST_TIMESTAMP_MILLIS = Instant.parse("2200-01-01T00:00:00Z").toEpochMilli()

private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

private val archiveSamples: Arb<ArchiveSample> = arbitrary {
    val tags = Arb.list(Arb.nonBlankText(), 0..3).bind().mapIndexed { index, name ->
        tagDtoFixture(id = "tag-${index + 1}", name = name)
    }
    var imageIndex = 0
    val images = linkedMapOf<String, ByteArray>()
    val memos = (1..Arb.int(0..3).bind()).map { memoNumber ->
        val memoImages = (1..Arb.int(0..2).bind()).map {
            imageIndex++
            val content = Arb.byteArray(Arb.int(0..64), Arb.byte()).bind()
            val fileName = "picked-$imageIndex" +
                Arb.text(0..12).bind().filterNot { it.isISOControl() || it == '/' || it == '\\' }
            imageDtoFixture(content, index = imageIndex, fileName = fileName)
                .also { images[it.archiveEntry] = content }
        }
        val createdAt = Arb.long(0L..LATEST_TIMESTAMP_MILLIS).bind()
        memoDtoFixture(
            id = "memo-$memoNumber:" + Arb.text(0..12).bind(),
            title = Arb.text().bind(),
            body = Arb.text(0..200).bind(),
            createdAt = createdAt,
            updatedAt = createdAt + Arb.long(0L..LATEST_TIMESTAMP_MILLIS - createdAt).bind(),
            isFavorite = Arb.boolean().bind(),
            tagIds = tags.filter { Arb.boolean().bind() }.map { it.id },
            images = memoImages
        )
    }
    ArchiveSample(
        manifest = manifestFixture(
            exportedAt = Arb.long(0L..LATEST_TIMESTAMP_MILLIS).bind(),
            tags = tags,
            memos = memos
        ),
        images = images
    )
}

private val arbitraryInputs: Arb<ByteArray> =
    Arb.byteArray(Arb.int(0..256), Arb.byte()).flatMap { bytes ->
        Arb.of(bytes, ZIP_SIGNATURE + bytes)
    }

private const val EXPANDED_ENTRY_BYTES = 1024 * 1024

private const val TRUNCATED_TAIL_BYTES = 512

private const val INVALID_UTF8_BYTE: Byte = -1
