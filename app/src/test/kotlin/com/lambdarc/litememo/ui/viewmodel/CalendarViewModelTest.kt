package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.epochMillis
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.domain.usecase.GetCurrentCalendarDateUseCase
import com.lambdarc.litememo.domain.usecase.ObserveCalendarMonthSummaryUseCase
import com.lambdarc.litememo.domain.usecase.ObserveMemosByCalendarDateUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.lambdarc.litememo.domain.usecase.SearchMemosUseCase
import com.lambdarc.litememo.ui.state.SearchUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    private lateinit var dispatcher: TestDispatcher
    private val zoneId = ZoneId.of("UTC")
    private val today = epochMillis("2026-05-15T12:00:00Z")

    @BeforeEach
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun nextMonthUpdatesSelectedMonth() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel()
        advanceUntilIdle()

        // Act
        viewModel.nextMonth()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(YearMonth.of(2026, 6), state.selectedMonth)
    }

    @Test
    fun selectDateUpdatesSelectedDate() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel()
        advanceUntilIdle()

        // Act
        viewModel.selectDate(LocalDate.of(2026, 5, 11))
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(LocalDate.of(2026, 5, 11), state.selectedDate)
    }

    @Test
    fun toggleCalendarExpandedCollapsesCalendar() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.first { !it.isLoading }.isCalendarExpanded)

        // Act
        viewModel.toggleCalendarExpanded()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertFalse(state.isCalendarExpanded)
    }

    @Test
    fun selectDateFromPickerUpdatesSelectedMonthAndDate() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel()
        advanceUntilIdle()

        // Act
        viewModel.selectDateFromPicker(epochMillis("2026-07-03T00:00:00Z"))
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertAll(
            { assertEquals(YearMonth.of(2026, 7), state.selectedMonth) },
            { assertEquals(LocalDate.of(2026, 7, 3), state.selectedDate) }
        )
    }

    @Test
    fun selectDateFromPickerUsesUtcSoDeviceWestOfUtcKeepsSameDay() = runTest(dispatcher) {
        // Arrange
        // DatePicker は日付を UTC midnight で符号化する。端末が UTC より西でも前日にずれない。
        val viewModel = calendarViewModel(zone = ZoneId.of("America/Los_Angeles"))
        advanceUntilIdle()

        // Act
        viewModel.selectDateFromPicker(epochMillis("2026-07-03T00:00:00Z"))
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(LocalDate.of(2026, 7, 3), state.selectedDate)
    }

    @Test
    fun selectDateFromPickerUsesUtcSoDeviceEastOfUtcDoesNotShiftToNextDay() = runTest(dispatcher) {
        // Arrange
        // UTC 日付として解釈するため、端末タイムゾーンでは翌日になる時刻でもずれない。
        val viewModel = calendarViewModel(zone = ZoneId.of("Asia/Tokyo"))
        advanceUntilIdle()

        // Act
        viewModel.selectDateFromPicker(epochMillis("2026-07-03T23:00:00Z"))
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(LocalDate.of(2026, 7, 3), state.selectedDate)
    }

    @Test
    fun boundarySelectedDateMillisUsesEpochForTokyoEpochDate() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel(zone = ZoneId.of("Asia/Tokyo"))
        advanceUntilIdle()
        viewModel.selectDate(LocalDate.of(1970, 1, 1))

        // Act
        // Boundary: epoch is valid on January 1 in Tokyo although local midnight is negative.
        val millis = viewModel.selectedDateMillis()

        // Assert
        assertEquals(0L, millis)
    }

    @Test
    fun boundarySelectedDateMillisUsesEpochForLosAngelesPreviousDate() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel(zone = ZoneId.of("America/Los_Angeles"))
        advanceUntilIdle()
        viewModel.selectDate(LocalDate.of(1969, 12, 31))

        // Act
        // Boundary: epoch belongs to December 31 in Los Angeles.
        val millis = viewModel.selectedDateMillis()

        // Assert
        assertEquals(0L, millis)
    }

    @Test
    fun boundarySelectedDateMillisReturnsNullBeforeFirstRepresentableLocalDate() =
        runTest(dispatcher) {
            // Arrange
            val viewModel = calendarViewModel(zone = ZoneId.of("America/Los_Angeles"))
            advanceUntilIdle()
            viewModel.selectDate(LocalDate.of(1969, 12, 30))

            // Act
            // Boundary: a date containing no non-negative instant cannot create a memo.
            val millis = viewModel.selectedDateMillis()

            // Assert
            assertEquals(null, millis)
        }

    @Test
    fun stateTransitionCloseSearchResetsSearch() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel()
        advanceUntilIdle()
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        viewModel.uiState.first {
            it.search.isActive && it.search.query == "shopping"
        }

        // Act
        // StateTransition: closing search resets active, query, error, and results together.
        viewModel.closeSearch()
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.search.isActive }

        // Assert
        assertEquals(SearchUiState(), state.search)
    }

    @Test
    fun stateTransitionSearchKeepsSelectedDate() = runTest(dispatcher) {
        // Arrange
        val selectedDate = LocalDate.of(2026, 5, 11)
        val viewModel = calendarViewModel(
            memoRepository = FakeMemoRepository(
                listOf(memoFixture(id = "shopping", title = "Shopping list"))
            )
        )
        advanceUntilIdle()
        viewModel.selectDate(selectedDate)

        // Act
        // StateTransition: search input and results do not replace Calendar date state.
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("shopping")
        advanceUntilIdle()
        val state = viewModel.uiState.first { it.search.results.isNotEmpty() }

        // Assert
        assertEquals(selectedDate, state.selectedDate)
    }

    @Test
    fun uiStateMarksOnlyMemoDatesWithDot() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel(
            memoRepository = FakeMemoRepository(
                listOf(
                    memoFixture(
                        id = "memo-1",
                        createdAt = epochMillis("2026-05-11T10:00:00Z")
                    )
                )
            )
        )

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(
            listOf(11),
            state.days.filter { day -> day.hasMemo }.map { day -> day.dayOfMonth }
        )
    }

    @Test
    fun uiStateReflectsMemoTagsForSelectedDate() = runTest(dispatcher) {
        // Arrange
        val tagId = TagId("tag-1")
        val viewModel = calendarViewModel(
            memoRepository = FakeMemoRepository(
                listOf(
                    memoFixture(
                        id = "memo-1",
                        createdAt = epochMillis("2026-05-15T10:00:00Z"),
                        tagIds = listOf(tagId)
                    )
                )
            ),
            tags = listOf(tagFixture(id = tagId.value, name = "仕事"))
        )

        // Act
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals(listOf("仕事"), state.memos.single().tags.map { it.name })
    }

    @Test
    fun normalUiStateMapsThumbnailPathFromFirstImage() = runTest(dispatcher) {
        // Arrange
        val viewModel = calendarViewModel(
            memoRepository = FakeMemoRepository(
                listOf(
                    memoFixture(
                        id = "memo-1",
                        createdAt = epochMillis("2026-05-15T10:00:00Z"),
                        images = listOf(memoImageFixture(fileName = "image-1.jpg"))
                    )
                )
            )
        )

        // Act
        // Normal: calendar memo cards resolve the first memo image into a thumbnail path.
        advanceUntilIdle()
        val state = viewModel.uiState.first { !it.isLoading }

        // Assert
        assertEquals("/images/image-1.jpg", state.memos.single().thumbnailPath)
    }

    @Test
    fun stateTransitionRetryReloadsSearchResultsAfterSearchError() = runTest(dispatcher) {
        // Arrange
        val memoRepository = RetryableSearchMemoRepository(
            delegate = FakeMemoRepository(
                listOf(
                    memoFixture(
                        id = "memo-1",
                        title = "Shopping",
                        body = "Buy coffee",
                        createdAt = epochMillis("2026-05-15T10:00:00Z")
                    )
                )
            )
        )
        val viewModel = calendarViewModel(memoRepository = memoRepository)
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("Shopping")
        advanceUntilIdle()
        viewModel.uiState.first { it.search.hasError }

        // Act
        // StateTransition: retry replaces the failed search snapshot with recovered results.
        memoRepository.allowSearch()
        viewModel.retry()
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            !it.search.hasError && it.search.results.isNotEmpty()
        }

        // Assert
        assertEquals(listOf("Shopping"), state.search.results.map { it.title })
    }

    @Test
    fun stateTransitionChangingQueryRecoversSearchAfterError() = runTest(dispatcher) {
        // Arrange
        val memoRepository = RetryableSearchMemoRepository(
            delegate = FakeMemoRepository(
                listOf(
                    memoFixture(
                        id = "memo-1",
                        title = "Coffee",
                        createdAt = epochMillis("2026-05-15T10:00:00Z")
                    )
                )
            )
        )
        val viewModel = calendarViewModel(memoRepository = memoRepository)
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("Shopping")
        advanceUntilIdle()
        viewModel.uiState.first { it.search.hasError }

        // Act
        // StateTransition/Error: a changed query starts a fresh search after the source recovers.
        memoRepository.allowSearch()
        viewModel.updateSearchQuery("Coffee")
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            !it.search.hasError && it.search.query == "Coffee" && it.search.results.isNotEmpty()
        }

        // Assert
        assertEquals(listOf("Coffee"), state.search.results.map { it.title })
    }

    @Test
    fun stateTransitionRetryReloadsCalendarAfterLoadError() = runTest(dispatcher) {
        // Arrange
        val memoRepository = RetryableCalendarMemoRepository(
            delegate = FakeMemoRepository(
                listOf(
                    memoFixture(
                        id = "memo-1",
                        title = "Recovered memo",
                        createdAt = epochMillis("2026-05-15T10:00:00Z")
                    )
                )
            )
        )
        val viewModel = calendarViewModel(memoRepository = memoRepository)
        viewModel.uiState.first { !it.isLoading && it.hasError }

        // Act
        // StateTransition/Error: retry resubscribes failed calendar data and restores the content.
        memoRepository.allowCalendarLoad()
        viewModel.retry()
        advanceUntilIdle()
        val state = viewModel.uiState.first {
            !it.hasError && it.memos.isNotEmpty()
        }

        // Assert
        assertAll(
            { assertEquals(listOf("Recovered memo"), state.memos.map { it.title }) },
            { assertEquals(YearMonth.of(2026, 5), state.selectedMonth) },
            { assertEquals(LocalDate.of(2026, 5, 15), state.selectedDate) }
        )
    }

    private fun calendarViewModel(
        memoRepository: MemoRepository = FakeMemoRepository(),
        tags: List<Tag> = emptyList(),
        zone: ZoneId = zoneId
    ): CalendarViewModel {
        val tagRepository = FakeTagRepository(tags)
        val displaySettingsRepository = FakeDisplaySettingsRepository()
        return CalendarViewModel(
            observeCalendarMonthSummaryUseCase = ObserveCalendarMonthSummaryUseCase(
                memoRepository = memoRepository,
                zoneId = zone
            ),
            observeMemosByCalendarDateUseCase = ObserveMemosByCalendarDateUseCase(
                memoRepository = memoRepository,
                displaySettingsRepository = displaySettingsRepository,
                zoneId = zone
            ),
            observeTagsUseCase = ObserveTagsUseCase(tagRepository),
            searchMemosUseCase = SearchMemosUseCase(
                memoRepository = memoRepository,
                displaySettingsRepository = displaySettingsRepository
            ),
            resolveMemoImagePathUseCase = ResolveMemoImagePathUseCase(FakeMemoImageStore()),
            getCurrentCalendarDateUseCase = GetCurrentCalendarDateUseCase(
                currentTimeProvider = MutableTimeProvider(TimestampMillis(today)),
                zoneId = zone
            ),
            zoneId = zone
        )
    }

    private class RetryableSearchMemoRepository(private val delegate: FakeMemoRepository) :
        MemoRepository by delegate {

        private var searchFails = true

        fun allowSearch() {
            searchFails = false
        }

        override fun observeActiveMemosBySearchQuery(query: SearchQuery): Flow<List<Memo>> =
            if (searchFails) {
                flow { throw IllegalStateException("Search failed.") }
            } else {
                delegate.observeActiveMemosBySearchQuery(query)
            }
    }

    private class RetryableCalendarMemoRepository(private val delegate: FakeMemoRepository) :
        MemoRepository by delegate {

        private var calendarLoadFails = true

        fun allowCalendarLoad() {
            calendarLoadFails = false
        }

        override fun observeActiveMemosCreatedBetween(range: TimestampRange): Flow<List<Memo>> =
            if (calendarLoadFails) {
                flow { throw IllegalStateException("Calendar load failed.") }
            } else {
                delegate.observeActiveMemosCreatedBetween(range)
            }
    }
}
