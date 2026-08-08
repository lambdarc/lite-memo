package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TimestampRangeTest {

    @Test
    fun normalConstructorRetainsInclusiveStartAndExclusiveEnd() {
        // Arrange
        val fromInclusive = TimestampMillis(1_000L)
        val toExclusive = TimestampMillis(2_000L)

        // Act
        // Normal: an ascending range retains both explicit boundaries
        val range = TimestampRange(fromInclusive, toExclusive)

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = TimestampMillis(1_000L),
                toExclusive = TimestampMillis(2_000L)
            ),
            range
        )
    }

    @Test
    fun normalAscendingRangeIsNotEmpty() {
        // Arrange
        val range = TimestampRange(TimestampMillis(1_000L), TimestampMillis(2_000L))

        // Act
        // Normal: distinct ascending boundaries represent a non-empty range
        val isEmpty = range.isEmpty

        // Assert
        assertFalse(isEmpty)
    }

    @Test
    fun boundaryEqualRangeIsEmpty() {
        // Arrange
        val range = TimestampRange(TimestampMillis(1_000L), TimestampMillis(1_000L))

        // Act
        // Boundary: equal boundaries represent a valid empty range
        val isEmpty = range.isEmpty

        // Assert
        assertTrue(isEmpty)
    }

    @Test
    fun errorDescendingRangeThrowsIllegalArgumentException() {
        // Act & Assert
        // Error: descending boundaries cannot enter the domain
        assertThrows(IllegalArgumentException::class.java) {
            TimestampRange(TimestampMillis(2_000L), TimestampMillis(1_000L))
        }
    }

}
