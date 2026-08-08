package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ExportFileReferenceTest {

    @Test
    fun constructorReturnsValueWhenInputIsAbsoluteUri() {
        // Act
        val reference = ExportFileReference("content://memo/export.json")

        // Assert
        assertEquals("content://memo/export.json", reference.value)
    }

    @Test
    fun constructorThrowsWhenInputIsBlank() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            ExportFileReference(" ")
        }
    }

    @Test
    fun constructorThrowsWhenInputIsEmpty() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            ExportFileReference("")
        }
    }

    @Test
    fun constructorThrowsWhenInputIsNotUri() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            ExportFileReference("not a uri")
        }
    }

    @Test
    fun constructorThrowsWhenInputIsRelativeUri() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            ExportFileReference("export.json")
        }
    }
}
