package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MemoTitleTest {

    @Test
    fun constructorReturnsTrimmedValueWhenInputHasSurroundingWhitespace() {
        // Act
        val title = MemoTitle("  title  ")

        // Assert
        assertEquals("title", title.value)
    }

    @Test
    fun constructorReturnsEmptyStringWhenValueIsBlank() {
        // Act
        val title = MemoTitle(" ")

        // Assert
        assertEquals("", title.value)
    }

    @ParameterizedTest
    @MethodSource("blankInputs")
    fun boundaryConstructorReturnsEmptyStringWhenValueContainsOnlyWhitespace(input: String) {
        // Act
        // Boundary: every whitespace class, full-width space included, becomes an empty title
        val title = MemoTitle(input)

        // Assert
        assertEquals("", title.value)
    }

    @Test
    fun boundaryConstructorTrimsSurroundingFullWidthSpace() {
        // Act
        // Boundary: full-width spaces typed with a Japanese IME are trimmed
        val title = MemoTitle("\u3000title\u3000")

        // Assert
        assertEquals("title", title.value)
    }

    private companion object {
        @JvmStatic
        fun blankInputs(): List<String> = listOf("", " ", "\u3000", "\n", "\t", " \u3000\n\t")
    }

}
