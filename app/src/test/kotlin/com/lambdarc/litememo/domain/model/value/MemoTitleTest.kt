package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

}
