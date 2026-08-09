package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ImageSourceReferenceTest {

    @Test
    fun normalInvokeReturnsValueWhenInputIsAbsoluteUri() {
        // Arrange
        val input = " content://memo/image.jpg "

        // Act
        // Normal: picker results are represented as absolute URI strings.
        val reference = ImageSourceReference(input)

        // Assert
        assertEquals("content://memo/image.jpg", reference.value)
    }

    @Test
    fun errorInvokeThrowsWhenInputIsBlank() {
        // Arrange
        val input = " "

        // Act & Assert
        // Error: blank source references cannot be copied.
        assertThrows(IllegalArgumentException::class.java) {
            ImageSourceReference(input)
        }
    }

    @Test
    fun errorInvokeThrowsWhenInputIsRelativeUri() {
        // Arrange
        val input = "image.jpg"

        // Act & Assert
        // Error: relative references do not identify a picker source.
        assertThrows(IllegalArgumentException::class.java) {
            ImageSourceReference(input)
        }
    }

}
