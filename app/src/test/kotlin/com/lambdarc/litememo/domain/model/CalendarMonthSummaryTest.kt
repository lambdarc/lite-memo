package com.lambdarc.litememo.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarMonthSummaryTest {

    @Test
    fun constructorAcceptsDaysBelongingToSummaryMonthWithoutDuplicates() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val days = listOf(
            CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 1)), 0),
            CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 2)), 3)
        )

        // Act
        val summary = CalendarMonthSummary(month, days)

        // Assert
        assertEquals(days, summary.days)
    }

    @Test
    fun constructorThrowsWhenDayBelongsToAnotherMonth() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val days = listOf(
            CalendarDaySummary(CalendarDate(LocalDate.of(2026, 6, 1)), 0)
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            CalendarMonthSummary(month, days)
        }
    }

    @Test
    fun constructorThrowsWhenDaysContainDuplicateDates() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val days = listOf(
            CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 1)), 0),
            CalendarDaySummary(CalendarDate(LocalDate.of(2026, 5, 1)), 1)
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            CalendarMonthSummary(month, days)
        }
    }
}
