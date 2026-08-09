package com.lambdarc.litememo.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CalendarDaySummaryTest {

    @Test
    fun constructorAcceptsZeroMemoCount() {
        // Act
        val summary = CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 1)), 0)

        // Assert
        assertEquals(0, summary.memoCount)
    }

    @Test
    fun constructorAcceptsPositiveMemoCount() {
        // Act
        val summary = CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 1)), 5)

        // Assert
        assertEquals(5, summary.memoCount)
    }

    @Test
    fun constructorThrowsWhenMemoCountIsNegative() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 1)), -1)
        }
    }
}
