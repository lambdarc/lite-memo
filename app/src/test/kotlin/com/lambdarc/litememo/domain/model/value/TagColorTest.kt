package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TagColorTest {

    @Test
    fun constructorReturnsArgbWhenValueIsMinimum() {
        // Act
        val color = TagColor(0x00000000L)

        // Assert
        assertEquals(0x00000000L, color.argb)
    }

    @Test
    fun constructorReturnsArgbWhenValueIsMaximum() {
        // Act
        val color = TagColor(0xFFFFFFFFL)

        // Assert
        assertEquals(0xFFFFFFFFL, color.argb)
    }

    @Test
    fun constructorThrowsWhenValueIsNegative() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            TagColor(-1L)
        }
    }

    @Test
    fun constructorThrowsWhenValueExceedsArgbRange() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            TagColor(0x1_00000000L)
        }
    }

}
