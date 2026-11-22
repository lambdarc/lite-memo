package com.lambdarc.litememo.data.export

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MemoArchiveImageCopierTest {

    @Test
    fun normalCopyVerifiedWritesSourceBytesToTarget() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 2048, seed = 1)
        val target = ByteArrayOutputStream()

        // Act
        // Normal: matching size and digest pass the verification untouched
        MemoArchiveImageCopier.copyVerified(
            ByteArrayInputStream(content),
            target,
            imageDtoFixture(content)
        )

        // Assert
        assertArrayEquals(content, target.toByteArray())
    }

    @Test
    fun boundaryCopyVerifiedAcceptsEmptySource() {
        // Arrange
        val content = ByteArray(0)
        val target = ByteArrayOutputStream()

        // Act
        // Boundary: zero bytes is the smallest declarable size
        MemoArchiveImageCopier.copyVerified(
            ByteArrayInputStream(content),
            target,
            imageDtoFixture(content)
        )

        // Assert
        assertArrayEquals(content, target.toByteArray())
    }

    @Test
    fun errorCopyVerifiedRejectsSourceOneByteLargerThanDeclared() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 129, seed = 2)
        val metadata = imageDtoFixture(content, sizeBytes = content.size - 1L)

        // Act & Assert
        // Error: one byte over the declared size is already a size mismatch
        assertArchiveFailure(MemoArchiveFailureReason.SIZE_MISMATCH) {
            MemoArchiveImageCopier.copyVerified(
                ByteArrayInputStream(content),
                ByteArrayOutputStream(),
                metadata
            )
        }
    }

    @Test
    fun errorCopyVerifiedRejectsSourceOneByteSmallerThanDeclared() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 127, seed = 3)
        val metadata = imageDtoFixture(content, sizeBytes = content.size + 1L)

        // Act & Assert
        // Error: one byte under the declared size is a size mismatch
        assertArchiveFailure(MemoArchiveFailureReason.SIZE_MISMATCH) {
            MemoArchiveImageCopier.copyVerified(
                ByteArrayInputStream(content),
                ByteArrayOutputStream(),
                metadata
            )
        }
    }

    @Test
    fun boundaryCopyVerifiedAcceptsUppercaseDeclaredDigest() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 512, seed = 4)
        val metadata = imageDtoFixture(content, sha256 = sha256Hex(content).uppercase())
        val target = ByteArrayOutputStream()

        // Act
        // Boundary: digest comparison ignores hex letter case
        MemoArchiveImageCopier.copyVerified(ByteArrayInputStream(content), target, metadata)

        // Assert
        assertArrayEquals(content, target.toByteArray())
    }

    @Test
    fun errorCopyVerifiedRejectsDigestOfDifferentBytes() {
        // Arrange
        val content = compressedImageBytes(sizeBytes = 512, seed = 5)
        val other = compressedImageBytes(sizeBytes = 512, seed = 6)
        val metadata = imageDtoFixture(content, sha256 = sha256Hex(other))

        // Act & Assert
        // Error: a well-formed digest of other bytes is a checksum mismatch, not a size mismatch
        assertArchiveFailure(MemoArchiveFailureReason.CHECKSUM_MISMATCH) {
            MemoArchiveImageCopier.copyVerified(
                ByteArrayInputStream(content),
                ByteArrayOutputStream(),
                metadata
            )
        }
    }
}
