package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lambdarc.litememo.domain.model.CalendarDate
import com.lambdarc.litememo.domain.model.CalendarMonth
import com.lambdarc.litememo.domain.model.CalendarMonthSummary
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.usecase.GetCurrentCalendarDateUseCase
import com.lambdarc.litememo.domain.usecase.ObserveCalendarMonthSummaryUseCase
import com.lambdarc.litememo.domain.usecase.ObserveMemosByCalendarDateUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.lambdarc.litememo.domain.usecase.SearchMemosUseCase
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.state.CalendarDayUiState
import com.lambdarc.litememo.ui.state.CalendarUiState
import com.lambdarc.litememo.ui.state.SearchUiState
import com.lambdarc.litememo.ui.state.searchMemoResults
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val observeCalendarMonthSummaryUseCase: ObserveCalendarMonthSummaryUseCase,
    private val observeMemosByCalendarDateUseCase: ObserveMemosByCalendarDateUseCase,
    private val observeTagsUseCase: ObserveTagsUseCase,
    private val searchMemosUseCase: SearchMemosUseCase,
    private val resolveMemoImagePathUseCase: ResolveMemoImagePathUseCase,
    getCurrentCalendarDateUseCase: GetCurrentCalendarDateUseCase,
    private val zoneId: ZoneId
) : ViewModel() {

    private val initialDate = getCurrentCalendarDateUseCase()
    private val selectedMonth = MutableStateFlow(CalendarMonth(YearMonth.from(initialDate.value)))
    private val selectedDate = MutableStateFlow(initialDate)
    private val isCalendarExpanded = MutableStateFlow(true)
    private val isDatePickerVisible = MutableStateFlow(false)
    private val searchControls = MutableStateFlow(SearchUiState())
    private val searchResults = searchControls.searchMemoResults(searchMemosUseCase)
    private val retryTrigger = MutableStateFlow(0)

    private val observedCalendarData = combine(
        selectedMonth.flatMapLatest { month ->
            observeCalendarMonthSummaryUseCase(month)
                .map<CalendarMonthSummary, CalendarMonthSummary?> { it }
                .catch { emit(null) }
        },
        selectedDate.flatMapLatest { date ->
            observeMemosByCalendarDateUseCase(date)
                .map<List<Memo>, List<Memo>?> { it }
                .catch { emit(null) }
        },
        observeTagsUseCase()
            .map<List<Tag>, List<Tag>?> { it }
            .catch { emit(null) }
    ) { monthSummary, memos, tags ->
        ObservedCalendarData(
            monthSummary = monthSummary,
            memos = memos,
            tags = tags
        )
    }

    private val uiControls = combine(
        isCalendarExpanded,
        isDatePickerVisible,
        searchControls
    ) { expanded, datePickerVisible, search ->
        CalendarUiControls(expanded, datePickerVisible, search)
    }

    val uiState: StateFlow<CalendarUiState> = retryTrigger.flatMapLatest {
        combine(
            observedCalendarData,
            selectedMonth,
            selectedDate,
            uiControls,
            searchResults
        ) { observed, month, date, controls, searchResult ->
            val hasError = observed.monthSummary == null ||
                observed.memos == null ||
                observed.tags == null
            val search = controls.search.withResult(searchResult) { searchHits ->
                if (observed.tags != null) {
                    MemoUiModel.fromDomain(
                        searchHits,
                        observed.tags,
                        resolveMemoImagePathUseCase::invoke
                    )
                } else {
                    emptyList()
                }
            }
            CalendarUiState(
                isLoading = false,
                hasError = hasError,
                selectedMonth = month.value,
                selectedDate = date.value,
                isCalendarExpanded = controls.expanded,
                isDatePickerVisible = controls.datePickerVisible,
                search = search,
                days = observed.monthSummary?.toDayUiStates(date) ?: emptyList(),
                memos = if (observed.memos != null && observed.tags != null) {
                    MemoUiModel.fromDomain(
                        observed.memos,
                        observed.tags,
                        resolveMemoImagePathUseCase::invoke
                    )
                } else {
                    emptyList()
                }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CalendarUiState(
            selectedMonth = selectedMonth.value.value,
            selectedDate = selectedDate.value.value
        )
    )

    fun previousMonth() {
        selectMonth(selectedMonth.value.value.minusMonths(1))
    }

    fun nextMonth() {
        selectMonth(selectedMonth.value.value.plusMonths(1))
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = CalendarDate(date)
        selectedMonth.value = CalendarMonth(YearMonth.from(date))
    }

    fun toggleCalendarExpanded() {
        isCalendarExpanded.value = !isCalendarExpanded.value
    }

    fun showDatePicker() {
        isDatePickerVisible.value = true
    }

    fun dismissDatePicker() {
        isDatePickerVisible.value = false
    }

    fun toggleSearch() {
        searchControls.update { search -> search.toggled() }
    }

    fun updateSearchQuery(query: String) {
        searchControls.update { search -> search.withQuery(query) }
    }

    fun closeSearch() {
        searchControls.update { search -> search.closed() }
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    fun selectedDateMillis(): Long {
        val date = selectedDate.value.value
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun selectDateFromPicker(millis: Long) {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        selectDate(date)
        dismissDatePicker()
    }

    private fun selectMonth(yearMonth: YearMonth) {
        selectedMonth.value = CalendarMonth(yearMonth)
        selectedDate.value = CalendarDate(coerceDateToMonth(selectedDate.value.value, yearMonth))
    }

    private fun coerceDateToMonth(date: LocalDate, yearMonth: YearMonth): LocalDate {
        val day = minOf(date.dayOfMonth, yearMonth.lengthOfMonth())
        return yearMonth.atDay(day)
    }

    private fun CalendarMonthSummary.toDayUiStates(selectedDate: CalendarDate) = days.map { day ->
        CalendarDayUiState(
            date = day.date.value,
            dayOfMonth = day.date.value.dayOfMonth,
            isSelected = day.date == selectedDate,
            hasMemo = day.memoCount > 0
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }

}

private data class CalendarUiControls(
    val expanded: Boolean,
    val datePickerVisible: Boolean,
    val search: SearchUiState
)

private data class ObservedCalendarData(
    val monthSummary: CalendarMonthSummary?,
    val memos: List<Memo>?,
    val tags: List<Tag>?
)
