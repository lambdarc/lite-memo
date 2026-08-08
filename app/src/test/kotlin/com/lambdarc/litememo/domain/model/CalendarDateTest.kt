package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CalendarDateTest {

    @Test
    fun normalToTimestampRangeReturnsUtcDayBoundaries() {
        // Arrange
        val date = CalendarDate(LocalDate.of(2026, 5, 11))

        // Act
        // Normal: a date maps from local start of day to the next local start of day
        val range = date.toTimestampRange(ZoneId.of("UTC"))

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = timestamp("2026-05-11T00:00:00Z"),
                toExclusive = timestamp("2026-05-12T00:00:00Z")
            ),
            range
        )
    }

    @Test
    fun boundaryToTimestampRangeUsesLocalBoundariesOnDstStartDate() {
        // Arrange
        val date = CalendarDate(LocalDate.of(2026, 3, 8))

        // Act
        // Boundary: DST start produces a 23-hour range from local date boundaries
        val range = date.toTimestampRange(ZoneId.of("America/New_York"))

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = timestamp("2026-03-08T05:00:00Z"),
                toExclusive = timestamp("2026-03-09T04:00:00Z")
            ),
            range
        )
    }

    @Test
    fun boundaryToTimestampRangeUsesLocalBoundariesOnDstEndDate() {
        // Arrange
        val date = CalendarDate(LocalDate.of(2026, 11, 1))

        // Act
        // Boundary: DST end produces a 25-hour range from local date boundaries
        val range = date.toTimestampRange(ZoneId.of("America/New_York"))

        // Assert
        assertEquals(
            TimestampRange(
                fromInclusive = timestamp("2026-11-01T04:00:00Z"),
                toExclusive = timestamp("2026-11-02T05:00:00Z")
            ),
            range
        )
    }

    @Test
    fun fromMapsTimestampToLocalDateInUtc() {
        // Arrange
        val timestamp = TimestampMillis(Instant.parse("2026-05-31T15:00:00Z").toEpochMilli())

        // Act
        val date = CalendarDate.from(timestamp, ZoneId.of("UTC"))

        // Assert
        assertEquals(CalendarDate(LocalDate.of(2026, 5, 31)), date)
    }

    @Test
    fun fromMapsTimestampToNextDayAtTokyoZoneBoundary() {
        // Arrange
        val timestamp = TimestampMillis(Instant.parse("2026-05-31T15:00:00Z").toEpochMilli())

        // Act
        val date = CalendarDate.from(timestamp, ZoneId.of("Asia/Tokyo"))

        // Assert
        assertEquals(CalendarDate(LocalDate.of(2026, 6, 1)), date)
    }

    private fun timestamp(value: String): TimestampMillis =
        TimestampMillis(Instant.parse(value).toEpochMilli())
}
