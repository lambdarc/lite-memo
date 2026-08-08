package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MemoImageIdTest {

    @Test
    fun normalInvokeTrimsAndKeepsValue() {
        // Arrange
        val input = " image-1 "

        // Act
        // Normal: image ids use the trimmed identifier.
        val id = MemoImageId(input)

        // Assert
        assertEquals("image-1", id.value)
    }

    @Test
    fun errorInvokeThrowsWhenBlank() {
        // Arrange
        val input = " "

        // Act & Assert
        // Error: blank image ids are invalid.
        assertThrows(IllegalArgumentException::class.java) {
            MemoImageId(input)
        }
    }

}
