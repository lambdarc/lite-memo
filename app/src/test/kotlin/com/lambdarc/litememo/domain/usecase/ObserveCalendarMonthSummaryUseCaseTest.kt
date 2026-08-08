package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.epochMillis
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.CalendarDate
import com.lambdarc.litememo.domain.model.CalendarMonth
import com.lambdarc.litememo.domain.model.CalendarMonthSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ObserveCalendarMonthSummaryUseCaseTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun invokeReturnsOneDayPerDateInMonth() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 2))
        val repository = FakeMemoRepository()

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(repository, zoneId)(month).first()

        // Assert
        assertEquals(month.toCalendarDates(), summary.days.map { it.date })
    }

    @Test
    fun invokeCountsMemosCreatedOnEachDate() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "first", createdAt = epochMillis("2026-05-11T08:00:00Z")),
                memoFixture(id = "second", createdAt = epochMillis("2026-05-11T20:00:00Z")),
                memoFixture(id = "third", createdAt = epochMillis("2026-05-12T09:00:00Z"))
            )
        )

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(repository, zoneId)(month).first()

        // Assert
        assertEquals(
            mapOf(
                CalendarDate(LocalDate.of(2026, 5, 11)) to 2,
                CalendarDate(LocalDate.of(2026, 5, 12)) to 1
            ),
            summary.days
                .filter { day -> day.memoCount > 0 }
                .associate { day -> day.date to day.memoCount }
        )
    }

    @Test
    fun invokeExcludesTrashedMemosFromCounts() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(id = "active", createdAt = epochMillis("2026-05-11T08:00:00Z")),
                memoFixture(
                    id = "trashed",
                    createdAt = epochMillis("2026-05-11T20:00:00Z"),
                    deletedAt = epochMillis("2026-05-12T00:00:00Z")
                )
            )
        )

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(repository, zoneId)(month).first()

        // Assert
        assertEquals(1, summary.memoCountOn(LocalDate.of(2026, 5, 11)))
    }

    @Test
    fun invokeExcludesMemoWhenOnlyUpdatedAtMatchesDate() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val targetDate = LocalDate.of(2026, 5, 11)
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(
                    id = "updated-on-target-date",
                    createdAt = epochMillis("2026-05-10T23:00:00Z"),
                    updatedAt = epochMillis("2026-05-11T01:00:00Z")
                )
            )
        )

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(repository, zoneId)(month).first()

        // Assert
        assertEquals(0, summary.memoCountOn(targetDate))
    }

    @Test
    fun invokeReturnsZeroMemoCountForEmptyDays() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 5))
        val emptyDate = LocalDate.of(2026, 5, 12)
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(
                    id = "created-on-other-date",
                    createdAt = epochMillis("2026-05-11T08:00:00Z")
                )
            )
        )

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(repository, zoneId)(month).first()

        // Assert
        assertEquals(0, summary.memoCountOn(emptyDate))
    }

    @Test
    fun invokeMapsCreatedAtUsingConfiguredZoneIdBoundary() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 6))
        val boundaryDate = LocalDate.of(2026, 6, 1)
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(
                    id = "jst-start-of-day",
                    createdAt = epochMillis("2026-05-31T15:00:00Z")
                )
            )
        )

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(
            memoRepository = repository,
            zoneId = ZoneId.of("Asia/Tokyo")
        )(month).first()

        // Assert
        assertEquals(1, summary.memoCountOn(boundaryDate))
    }

    @Test
    fun invokeExcludesMemosCreatedAtNextMonthStartBoundary() = runTest {
        // Arrange
        val month = CalendarMonth(YearMonth.of(2026, 6))
        val repository = FakeMemoRepository(
            listOf(
                memoFixture(
                    id = "next-month-start",
                    createdAt = epochMillis("2026-07-01T00:00:00Z")
                )
            )
        )

        // Act
        val summary = ObserveCalendarMonthSummaryUseCase(repository, zoneId)(month).first()

        // Assert
        assertEquals(0, summary.days.sumOf { day -> day.memoCount })
    }

    private fun CalendarMonthSummary.memoCountOn(date: LocalDate): Int =
        days.find { day -> day.date == CalendarDate(date) }?.memoCount
            ?: throw AssertionError(
                "Expected day for $date in CalendarMonthSummary but none found."
            )

}
