package com.appvoyager.litememo.data.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoArchiveLayoutTest {

    @Test
    fun normalImageEntryNameIsZeroPaddedSequentialPath() {
        // Act
        // Normal: entry names come from a sequence, never from domain ids or original file names
        val entryName = MemoArchiveLayout.imageEntryName(1)

        // Assert
        assertEquals("images/00000001", entryName)
    }

    @Test
    fun errorImageEntryNameRejectsZeroIndex() {
        // Act & Assert
        // Error: numbering starts at 1, so zero falls outside the rule
        assertThrows(IllegalArgumentException::class.java) {
            MemoArchiveLayout.imageEntryName(0)
        }
    }

    @Test
    fun boundaryIsImageEntryNameRejectsNonPaddedNumber() {
        // Act
        // Boundary: image entry numbers must use the generated padding
        val isImageEntryName = MemoArchiveLayout.isImageEntryName("images/1")

        // Assert
        assertFalse(isImageEntryName)
    }

    @Test
    fun boundaryIsImageEntryNameRejectsFileExtension() {
        // Act
        // Boundary: generated image entries do not include file extensions
        val isImageEntryName = MemoArchiveLayout.isImageEntryName("images/00000001.jpg")

        // Assert
        assertFalse(isImageEntryName)
    }

    @Test
    fun boundaryIsImageEntryNameRejectsNestedPath() {
        // Act
        // Boundary: generated image entries cannot contain an additional path segment
        val isImageEntryName = MemoArchiveLayout.isImageEntryName("images/sub/00000001")

        // Assert
        assertFalse(isImageEntryName)
    }

    @Test
    fun boundaryIsImageEntryNameRejectsManifestEntry() {
        // Act
        // Boundary: the manifest is not classified as an image entry
        val isImageEntryName = MemoArchiveLayout.isImageEntryName("manifest.json")

        // Assert
        assertFalse(isImageEntryName)
    }

    @Test
    fun errorIsSafeEntryNameRejectsParentTraversal() {
        // Act
        // Error: parent traversal cannot escape the archive root
        val isSafe = MemoArchiveLayout.isSafeEntryName("../evil.txt")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsNestedParentTraversal() {
        // Act
        // Error: nested parent traversal cannot escape the image directory
        val isSafe = MemoArchiveLayout.isSafeEntryName("images/../../evil.txt")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsAbsolutePath() {
        // Act
        // Error: absolute paths cannot target the host file system
        val isSafe = MemoArchiveLayout.isSafeEntryName("/etc/passwd")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsBackslashSeparator() {
        // Act
        // Error: backslashes cannot introduce platform-specific paths
        val isSafe = MemoArchiveLayout.isSafeEntryName("images\\00000001")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsWindowsAbsolutePath() {
        // Act
        // Error: drive-qualified paths cannot target the host file system
        val isSafe = MemoArchiveLayout.isSafeEntryName("C:/images/00000001")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsControlCharacter() {
        // Act
        // Error: control characters cannot appear in archive entry names
        val isSafe = MemoArchiveLayout.isSafeEntryName("images/" + Char(1))

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsCurrentDirectorySegment() {
        // Act
        // Error: current-directory segments are not canonical archive paths
        val isSafe = MemoArchiveLayout.isSafeEntryName("./manifest.json")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun errorIsSafeEntryNameRejectsBlankName() {
        // Act
        // Error: blank entry names do not identify archive content
        val isSafe = MemoArchiveLayout.isSafeEntryName(" ")

        // Assert
        assertFalse(isSafe)
    }

    @Test
    fun normalIsSafeEntryNameAcceptsManifestEntry() {
        // Act
        // Normal: the generated manifest entry is accepted as safe
        val isSafe = MemoArchiveLayout.isSafeEntryName(MemoArchiveLayout.MANIFEST_ENTRY_NAME)

        // Assert
        assertTrue(isSafe)
    }

    @Test
    fun normalIsSafeEntryNameAcceptsGeneratedImageEntry() {
        // Act
        // Normal: a generated image entry is accepted as safe
        val isSafe = MemoArchiveLayout.isSafeEntryName(
            MemoArchiveLayout.imageEntryName(12345678)
        )

        // Assert
        assertTrue(isSafe)
    }
}
