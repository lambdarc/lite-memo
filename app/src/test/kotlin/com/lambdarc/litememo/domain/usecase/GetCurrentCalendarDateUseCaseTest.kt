package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.model.CalendarDate
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class GetCurrentCalendarDateUseCaseTest {

    @Test
    fun normalInvokeReturnsTodayInConfiguredZone() {
        // Arrange
        val now = TimestampMillis(Instant.parse("2026-06-01T20:00:00Z").toEpochMilli())
        val useCase = GetCurrentCalendarDateUseCase(
            MutableTimeProvider(now),
            ZoneId.of("Asia/Tokyo")
        )

        // Act
        // Normal: the current instant is resolved into a local date, not a UTC date.
        val date = useCase()

        // Assert
        assertEquals(CalendarDate(LocalDate.of(2026, 6, 2)), date)
    }

}
