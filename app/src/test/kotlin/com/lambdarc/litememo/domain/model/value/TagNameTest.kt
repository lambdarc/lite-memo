package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

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

    @ParameterizedTest
    @MethodSource("blankInputs")
    fun errorConstructorThrowsWhenValueContainsOnlyWhitespace(input: String) {
        // Act & Assert
        // Error: every whitespace class, full-width space included, is rejected
        assertThrows(IllegalArgumentException::class.java) { TagName(input) }
    }

    @Test
    fun boundaryConstructorTrimsSurroundingFullWidthSpace() {
        // Act
        // Boundary: full-width spaces typed with a Japanese IME are trimmed
        val name = TagName("\u3000Work\u3000")

        // Assert
        assertEquals("Work", name.value)
    }

    @Test
    fun normalConstructorKeepsInnerWhitespace() {
        // Act
        // Normal: only surrounding whitespace is removed
        val name = TagName(" Work Item ")

        // Assert
        assertEquals("Work Item", name.value)
    }

    private companion object {
        @JvmStatic
        fun blankInputs(): List<String> = listOf("", " ", "\u3000", "\n", "\t", " \u3000\n\t")
    }

}
