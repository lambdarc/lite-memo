package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.FakeMemoRepository
import com.appvoyager.litememo.domain.epochMillis
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.CalendarDate
import com.appvoyager.litememo.domain.repository.FakeDisplaySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class ObserveMemosByCalendarDateUseCaseTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun invokeReturnsOnlyMemosCreatedOnSelectedDate() = runTest {
        // Arrange
        val selectedDate = CalendarDate(LocalDate.of(2026, 5, 11))
        val included = memoFixture(id = "included", createdAt = epochMillis("2026-05-11T08:00:00Z"))
        val createdOnOtherDate = memoFixture(
            id = "created-on-other-date",
            createdAt = epochMillis("2026-05-10T23:00:00Z"),
            updatedAt = epochMillis("2026-05-11T01:00:00Z")
        )
        val repository = FakeMemoRepository(listOf(createdOnOtherDate, included))

        // Act
        val memos = ObserveMemosByCalendarDateUseCase(
            repository,
            FakeDisplaySettingsRepository(),
            zoneId
        )(selectedDate).first()

        // Assert
        assertEquals(listOf("included"), memos.map { memo -> memo.id.value })
    }

    @Test
    fun invokeExcludesTrashedMemos() = runTest {
        // Arrange
        val selectedDate = CalendarDate(LocalDate.of(2026, 5, 11))
        val active = memoFixture(id = "active", createdAt = epochMillis("2026-05-11T08:00:00Z"))
        val trashed = memoFixture(
            id = "trashed",
            createdAt = epochMillis("2026-05-11T09:00:00Z"),
            deletedAt = epochMillis("2026-05-12T00:00:00Z")
        )
        val repository = FakeMemoRepository(listOf(active, trashed))

        // Act
        val memos = ObserveMemosByCalendarDateUseCase(
            repository,
            FakeDisplaySettingsRepository(),
            zoneId
        )(selectedDate).first()

        // Assert
        assertEquals(listOf("active"), memos.map { memo -> memo.id.value })
    }

    @Test
    fun invokeSortsMemosByUpdatedAtDescending() = runTest {
        // Arrange
        val selectedDate = CalendarDate(LocalDate.of(2026, 5, 11))
        val olderUpdatedAt = memoFixture(
            id = "older-updated-at",
            createdAt = epochMillis("2026-05-11T09:00:00Z"),
            updatedAt = epochMillis("2026-05-11T10:00:00Z")
        )
        val newestUpdatedAt = memoFixture(
            id = "newest-updated-at",
            createdAt = epochMillis("2026-05-11T08:00:00Z"),
            updatedAt = epochMillis("2026-05-11T12:00:00Z")
        )
        val middleUpdatedAt = memoFixture(
            id = "middle-updated-at",
            createdAt = epochMillis("2026-05-11T10:00:00Z"),
            updatedAt = epochMillis("2026-05-11T11:00:00Z")
        )
        val repository = FakeMemoRepository(
            listOf(olderUpdatedAt, newestUpdatedAt, middleUpdatedAt)
        )

        // Act
        val memos = ObserveMemosByCalendarDateUseCase(
            repository,
            FakeDisplaySettingsRepository(),
            zoneId
        )(selectedDate).first()

        // Assert
        assertEquals(
            listOf("newest-updated-at", "middle-updated-at", "older-updated-at"),
            memos.map { memo -> memo.id.value }
        )
    }

    @Test
    fun invokeMapsCreatedAtUsingConfiguredZoneIdBoundary() = runTest {
        // Arrange
        val selectedDate = CalendarDate(LocalDate.of(2026, 6, 1))
        val included = memoFixture(
            id = "jst-start-of-day",
            createdAt = epochMillis("2026-05-31T15:00:00Z")
        )
        val excluded = memoFixture(
            id = "next-jst-day",
            createdAt = epochMillis("2026-06-01T15:00:00Z")
        )
        val repository = FakeMemoRepository(listOf(excluded, included))

        // Act
        val memos = ObserveMemosByCalendarDateUseCase(
            memoRepository = repository,
            displaySettingsRepository = FakeDisplaySettingsRepository(),
            zoneId = ZoneId.of("Asia/Tokyo")
        )(selectedDate).first()

        // Assert
        assertEquals(listOf("jst-start-of-day"), memos.map { memo -> memo.id.value })
    }

}
