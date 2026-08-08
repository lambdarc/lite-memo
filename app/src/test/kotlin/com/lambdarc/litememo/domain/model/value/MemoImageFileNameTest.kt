package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MemoImageFileNameTest {

    @Test
    fun normalInvokeKeepsPlainFileName() {
        // Arrange
        val input = " image-1.jpg "

        // Act
        // Normal: plain file names are trimmed and kept.
        val fileName = MemoImageFileName(input)

        // Assert
        assertEquals("image-1.jpg", fileName.value)
    }

    @Test
    fun errorInvokeThrowsWhenContainsSlash() {
        // Arrange
        val input = "memo/image-1.jpg"

        // Act & Assert
        // Error: forward slashes would escape the image directory.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageFileName(input)
        }
    }

    @Test
    fun errorInvokeThrowsWhenContainsBackslash() {
        // Arrange
        val input = "memo\\image-1.jpg"

        // Act & Assert
        // Error: backslashes would escape the image directory on Windows paths.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageFileName(input)
        }
    }

    @Test
    fun errorInvokeThrowsWhenDotDot() {
        // Arrange
        val input = ".."

        // Act & Assert
        // Error: relative path references are not valid file names.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageFileName(input)
        }
    }

    @Test
    fun errorInvokeThrowsWhenDot() {
        // Arrange
        val input = "."

        // Act & Assert
        // Error: current directory references are not valid file names.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageFileName(input)
        }
    }

    @Test
    fun errorInvokeThrowsWhenContainsControlCharacter() {
        // Arrange
        val input = "image\u0000.jpg"

        // Act & Assert
        // Error: control characters are not valid file-name content.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageFileName(input)
        }
    }

    @Test
    fun errorInvokeThrowsWhenBlank() {
        // Arrange
        val input = " "

        // Act & Assert
        // Error: blank file names cannot address an image.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageFileName(input)
        }
    }

}
