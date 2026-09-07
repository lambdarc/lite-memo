package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarMonthTest {

    @Test
    fun normalToTimestampRangeReturnsMonthBoundaries() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 4))

        // Act
        // Normal: a month maps from its first local day to the next month's first local day
        val range = month.toTimestampRange(ZoneId.of("UTC"))

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = timestamp("2026-04-01T00:00:00Z"),
                toExclusive = timestamp("2026-05-01T00:00:00Z")
            ),
            range
        )
    }

    @Test
    fun boundaryToTimestampRangeHandlesLeapFebruary() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2024, 2))

        // Act
        // Boundary: leap February ends at March 1 after February 29
        val range = month.toTimestampRange(ZoneId.of("UTC"))

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = timestamp("2024-02-01T00:00:00Z"),
                toExclusive = timestamp("2024-03-01T00:00:00Z")
            ),
            range
        )
    }

    @Test
    fun boundaryToTimestampRangeHandlesYearBoundary() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 12))

        // Act
        // Boundary: December ends at January 1 of the next year
        val range = month.toTimestampRange(ZoneId.of("UTC"))

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = timestamp("2026-12-01T00:00:00Z"),
                toExclusive = timestamp("2027-01-01T00:00:00Z")
            ),
            range
        )
    }

    @Test
    fun toCalendarDatesReturnsTwentyNineDaysForLeapFebruary() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2024, 2))

        // Act
        val dates = month.toCalendarDates()

        // Assert
        assertEquals(29, dates.size)
    }

    @Test
    fun toCalendarDatesReturnsTwentyEightDaysForNonLeapFebruary() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2025, 2))

        // Act
        val dates = month.toCalendarDates()

        // Assert
        assertEquals(28, dates.size)
    }

    @Test
    fun toCalendarDatesReturnsAscendingDatesFromFirstDayOfMonth() {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 4))

        // Act
        val dates = month.toCalendarDates()

        // Assert
        assertEquals(
            (1..30).map { day -> CalendarDate(LocalDate.of(2026, 4, day)) },
            dates
        )
    }

    @Test
    fun boundaryToTimestampRangeClampsTokyoEpochBoundary() {
        // Arrange
        val value = CalendarMonth(YearMonth.of(1970, 1))

        // Act
        // Boundary: the local start precedes the earliest persistable timestamp.
        val range = value.toTimestampRange(ZoneId.of("Asia/Tokyo"))

        // Assert
        assertEquals(
            TimestampRange(TimestampMillis(0L), timestamp("1970-01-31T15:00:00Z")),
            range
        )
    }

    @Test
    fun boundaryToTimestampRangeReturnsEmptyRangeBeforeEpoch() {
        // Arrange
        val value = CalendarMonth(YearMonth.of(1960, 1))

        // Act
        // Boundary: dates entirely before the epoch cannot contain saved memos.
        val range = value.toTimestampRange(ZoneId.of("UTC"))

        // Assert
        assertEquals(TimestampRange(TimestampMillis(0L), TimestampMillis(0L)), range)
    }

    private fun timestamp(value: String): TimestampMillis =
        TimestampMillis(Instant.parse(value).toEpochMilli())
}
