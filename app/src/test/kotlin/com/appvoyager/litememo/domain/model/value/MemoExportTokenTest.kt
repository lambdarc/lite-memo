package com.appvoyager.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MemoExportTokenTest {

    @Test
    fun normalConstructorKeepsOpaqueValue() {
        // Arrange
        val value = "prepared-1"

        // Act
        // Normal: UI can retain the token without learning a file path.
        val token = MemoExportToken(value)

        // Assert
        assertEquals(value, token.value)
    }

    @Test
    fun boundaryConstructorRejectsBlankValue() {
        // Act & Assert
        // Boundary: an unusable session identifier cannot enter the domain.
        assertThrows(IllegalArgumentException::class.java) {
            MemoExportToken(" ")
        }
    }

}
