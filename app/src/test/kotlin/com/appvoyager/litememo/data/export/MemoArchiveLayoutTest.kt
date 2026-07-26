package com.appvoyager.litememo.data.export

import org.junit.jupiter.api.Assertions.assertAll
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
    fun errorImageEntryNameRejectsNonPositiveIndex() {
        // Act & Assert
        // Error: numbering starts at 1, so zero and negatives fall outside the rule
        assertThrows(IllegalArgumentException::class.java) {
            MemoArchiveLayout.imageEntryName(0)
        }
    }

    @Test
    fun boundaryIsImageEntryNameRejectsNamesOutsideGeneratedPattern() {
        // Act
        // Boundary: image entry names off the generation rule are rejected
        val results = listOf(
            "images/1",
            "images/00000001.jpg",
            "images/sub/00000001",
            "manifest.json"
        ).map { MemoArchiveLayout.isImageEntryName(it) }

        // Assert
        assertEquals(List(results.size) { false }, results)
    }

    @Test
    fun errorIsSafeEntryNameRejectsNamesThatEscapeTheArchive() {
        // Act & Assert
        // Error: absolute paths, parent traversal, backslashes and control characters are rejected
        assertAll(
            { assertFalse(MemoArchiveLayout.isSafeEntryName("../evil.txt")) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName("images/../../evil.txt")) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName("/etc/passwd")) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName("images\\00000001")) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName("C:/images/00000001")) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName("images/" + Char(1))) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName("./manifest.json")) },
            { assertFalse(MemoArchiveLayout.isSafeEntryName(" ")) }
        )
    }

    @Test
    fun normalIsSafeEntryNameAcceptsGeneratedLayout() {
        // Act
        // Normal: entry names produced by the layout are accepted as safe
        val results = listOf(
            MemoArchiveLayout.MANIFEST_ENTRY_NAME,
            MemoArchiveLayout.imageEntryName(12345678)
        ).map { MemoArchiveLayout.isSafeEntryName(it) }

        // Assert
        assertTrue(results.all { it })
    }
}
