package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class SearchQueryTest {

    @Test
    fun constructorReturnsTrimmedValueWhenInputHasSurroundingWhitespace() {
        // Act
        val query = SearchQuery("  shopping  ")

        // Assert
        assertEquals("shopping", query.value)
    }

    @Test
    fun constructorThrowsWhenValueIsBlank() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            SearchQuery(" ")
        }
    }

    @Test
    fun fromOrNullReturnsNullWhenValueIsBlank() {
        // Act
        val query = SearchQuery.fromOrNull(" ")

        // Assert
        assertNull(query)
    }

    @ParameterizedTest
    @MethodSource("blankInputs")
    fun errorConstructorThrowsWhenValueContainsOnlyWhitespace(input: String) {
        // Act & Assert
        // Error: every whitespace class, full-width space included, is rejected
        assertThrows(IllegalArgumentException::class.java) { SearchQuery(input) }
    }

    @ParameterizedTest
    @MethodSource("blankInputs")
    fun boundaryFromOrNullReturnsNullWhenValueContainsOnlyWhitespace(input: String) {
        // Act
        // Boundary: whitespace-only input means no search instead of an error
        val query = SearchQuery.fromOrNull(input)

        // Assert
        assertNull(query)
    }

    @Test
    fun boundaryConstructorTrimsSurroundingFullWidthSpace() {
        // Act
        // Boundary: full-width spaces typed with a Japanese IME are trimmed
        val query = SearchQuery("\u3000shopping\u3000")

        // Assert
        assertEquals("shopping", query.value)
    }

    private companion object {
        @JvmStatic
        fun blankInputs(): List<String> = listOf("", " ", "\u3000", "\n", "\t", " \u3000\n\t")
    }

}
