package com.appvoyager.litememo.data.export

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

class MemoArchiveManifestValidatorTest {

    private val limits = MemoArchiveLimits.DEFAULT

    @Test
    fun normalValidateAcceptsImageOnlyMemo() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    title = "",
                    body = "",
                    images = listOf(imageDtoFixture(content))
                )
            )
        )

        // Act & Assert
        // Normal: an image-only memo is a valid archive structure
        assertDoesNotThrow { MemoArchiveManifestValidator.validate(manifest, limits) }
    }

    @Test
    fun errorValidateRejectsUnsupportedVersion() {
        // Arrange
        val manifest = manifestFixture(version = MemoArchiveLayout.VERSION + 1)

        // Act & Assert
        // Error: an unsupported version rejects the whole archive
        assertArchiveFailure(MemoArchiveFailureReason.UNSUPPORTED_VERSION) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsDuplicateMemoIds() {
        // Arrange
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(id = "memo-1"), memoDtoFixture(id = "memo-1"))
        )

        // Act & Assert
        // Error: duplicate memo ids are rejected
        assertArchiveFailure(MemoArchiveFailureReason.DUPLICATE_IDENTIFIER) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsDuplicateTagIds() {
        // Arrange
        val manifest = manifestFixture(
            tags = listOf(tagDtoFixture(id = "tag-1"), tagDtoFixture(id = "tag-1"))
        )

        // Act & Assert
        // Error: duplicate tag ids are rejected
        assertArchiveFailure(MemoArchiveFailureReason.DUPLICATE_IDENTIFIER) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsNegativeExportTimestamp() {
        // Arrange
        val manifest = manifestFixture(exportedAt = -1L)

        // Act & Assert
        // Error: values that cannot become a domain timestamp invalidate the archive.
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsBlankTagName() {
        // Arrange
        val manifest = manifestFixture(tags = listOf(tagDtoFixture(name = " ")))

        // Act & Assert
        // Error: values that cannot become a tag name invalidate the archive.
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsTagColorOutsideArgbRange() {
        // Arrange
        val manifest = manifestFixture(
            tags = listOf(tagDtoFixture().copy(colorArgb = 0x1_00000000L))
        )

        // Act & Assert
        // Error: values that cannot become a tag color invalidate the archive.
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsMemoUpdatedBeforeCreation() {
        // Arrange
        val manifest = manifestFixture(
            memos = listOf(memoDtoFixture(createdAt = 2_000L, updatedAt = 1_000L))
        )

        // Act & Assert
        // Error: values that cannot become a memo aggregate invalidate the archive.
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsImageIdSharedByDifferentMemos() {
        // Arrange
        val first = compressedImageBytes(sizeBytes = 64, seed = 1)
        val second = compressedImageBytes(sizeBytes = 64, seed = 2)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(id = "memo-1", images = listOf(imageDtoFixture(first, index = 1))),
                memoDtoFixture(
                    id = "memo-2",
                    images = listOf(imageDtoFixture(second, index = 2, id = "image-1"))
                )
            )
        )

        // Act & Assert
        // Error: memo_images.id is a database-wide primary key, so it stays unique across the archive
        assertArchiveFailure(MemoArchiveFailureReason.DUPLICATE_IDENTIFIER) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsDuplicateArchiveEntries() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(
                        imageDtoFixture(content, index = 1),
                        imageDtoFixture(content, index = 2, archiveEntry = "images/00000001")
                    )
                )
            )
        )

        // Act & Assert
        // Error: a manifest pointing several images at one archive entry is rejected
        assertArchiveFailure(MemoArchiveFailureReason.DUPLICATE_IDENTIFIER) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsArchiveEntryOutsideGeneratedLayout() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(imageDtoFixture(content, archiveEntry = "../evil.txt"))
                )
            )
        )

        // Act & Assert
        // Error: entry names off the generation rule are rejected while reading the manifest
        assertArchiveFailure(MemoArchiveFailureReason.INVALID_ENTRY_NAME) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsImageFileNameWithPathSeparator() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(imageDtoFixture(content, fileName = "sub/picked.jpg"))
                )
            )
        )

        // Act & Assert
        // Error: values that cannot become a stored file name are rejected at the archive level
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun errorValidateRejectsChecksumThatIsNotSha256Hex() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(images = listOf(imageDtoFixture(content, sha256 = "not-a-digest")))
            )
        )

        // Act & Assert
        // Error: a manifest whose checksum is not SHA-256 hex is rejected
        assertArchiveFailure(MemoArchiveFailureReason.MALFORMED_ARCHIVE) {
            MemoArchiveManifestValidator.validate(manifest, limits)
        }
    }

    @Test
    fun boundaryValidateRejectsSingleImageOverImageLimit() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(images = listOf(imageDtoFixture(content, sizeBytes = 9)))
            )
        )

        // Act & Assert
        // Boundary: a declaration above the single-image limit is rejected
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            MemoArchiveManifestValidator.validate(manifest, limits.copy(maxImageBytes = 8))
        }
    }

    @Test
    fun boundaryValidateRejectsTotalImageBytesOverLimit() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(
                        imageDtoFixture(content, index = 1, sizeBytes = 8),
                        imageDtoFixture(content, index = 2, sizeBytes = 8)
                    )
                )
            )
        )

        // Act & Assert
        // Boundary: a declaration above the total extracted byte limit is rejected
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            MemoArchiveManifestValidator.validate(
                manifest,
                limits.copy(maxImageBytes = 8, maxTotalImageBytes = 15)
            )
        }
    }

    @Test
    fun boundaryValidateRejectsEntryCountOverLimit() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 64, seed = 1)
        val manifest = manifestFixture(
            memos = listOf(
                memoDtoFixture(
                    images = listOf(
                        imageDtoFixture(content, index = 1),
                        imageDtoFixture(content, index = 2)
                    )
                )
            )
        )

        // Act & Assert
        // Boundary: a declaration above the total entry count limit, manifest included, is rejected
        assertArchiveFailure(MemoArchiveFailureReason.LIMIT_EXCEEDED) {
            MemoArchiveManifestValidator.validate(manifest, limits.copy(maxEntryCount = 2))
        }
    }
}
