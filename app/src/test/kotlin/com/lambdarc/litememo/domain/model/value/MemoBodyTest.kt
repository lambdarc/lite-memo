package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoBodyTest {

    @Test
    fun constructorReturnsInputValueWhenInputHasSurroundingWhitespace() {
        // Act
        val body = MemoBody("  body  ")

        // Assert
        assertEquals("  body  ", body.value)
    }

    @Test
    fun constructorReturnsInputValueWhenValueIsBlank() {
        // Act
        val body = MemoBody(" ")

        // Assert
        assertEquals(" ", body.value)
    }

    @Test
    fun constructorReturnsEmptyStringWhenValueIsEmpty() {
        // Act
        val body = MemoBody("")

        // Assert
        assertEquals("", body.value)
    }

}
