package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TimestampMillisTest {

    @Test
    fun constructorReturnsValueWhenTimestampIsZero() {
        // Act
        val timestamp = TimestampMillis(0L)

        // Assert
        assertEquals(0L, timestamp.value)
    }

    @Test
    fun constructorThrowsWhenValueIsNegative() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            TimestampMillis(-1L)
        }
    }

}
