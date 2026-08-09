package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TagIdTest {

    @Test
    fun constructorReturnsTrimmedValueWhenInputHasSurroundingWhitespace() {
        // Act
        val id = TagId(" tag-1 ")

        // Assert
        assertEquals("tag-1", id.value)
    }

    @Test
    fun constructorThrowsWhenValueIsBlank() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            TagId("")
        }
    }

    @Test
    fun constructorThrowsWhenValueIsWhitespace() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            TagId("   ")
        }
    }

}
