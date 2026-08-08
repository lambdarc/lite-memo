package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TagNameTest {

    @Test
    fun constructorReturnsTrimmedValueWhenInputHasSurroundingWhitespace() {
        // Act
        val name = TagName("  Work  ")

        // Assert
        assertEquals("Work", name.value)
    }

    @Test
    fun constructorThrowsWhenValueIsBlank() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            TagName("  ")
        }
    }

}
