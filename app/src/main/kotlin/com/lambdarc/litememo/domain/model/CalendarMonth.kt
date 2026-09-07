package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import java.time.YearMonth
import java.time.ZoneId

@JvmInline
value class CalendarMonth(val value: YearMonth) {

    fun toTimestampRange(zoneId: ZoneId): TimestampRange = TimestampRange(
        fromInclusive = TimestampMillis(
            value.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli().coerceAtLeast(0L)
        ),
        toExclusive = TimestampMillis(
            value.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                .coerceAtLeast(0L)
        )
    )

    fun toCalendarDates(): List<CalendarDate> = (1..value.lengthOfMonth()).map { dayOfMonth ->
        CalendarDate(value.atDay(dayOfMonth))
    }

}
