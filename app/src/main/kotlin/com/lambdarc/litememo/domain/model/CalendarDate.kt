package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@JvmInline
value class CalendarDate(val value: LocalDate) {

    fun toTimestampRange(zoneId: ZoneId): TimestampRange = TimestampRange(
        fromInclusive = TimestampMillis(
            value.atStartOfDay(zoneId).toInstant().toEpochMilli().coerceAtLeast(0L)
        ),
        toExclusive = TimestampMillis(
            value.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli().coerceAtLeast(0L)
        )
    )

    companion object {
        fun from(timestamp: TimestampMillis, zoneId: ZoneId): CalendarDate =
            CalendarDate(Instant.ofEpochMilli(timestamp.value).atZone(zoneId).toLocalDate())
    }

}
