package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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

}
