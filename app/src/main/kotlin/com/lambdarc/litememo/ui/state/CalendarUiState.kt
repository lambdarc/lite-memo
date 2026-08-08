package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.MemoUiModel
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val selectedMonth: YearMonth? = null,
    val selectedDate: LocalDate? = null,
    val isCalendarExpanded: Boolean = true,
    val isDatePickerVisible: Boolean = false,
    val search: SearchUiState = SearchUiState(),
    val days: List<CalendarDayUiState> = emptyList(),
    val memos: List<MemoUiModel> = emptyList()
)

data class CalendarDayUiState(
    val date: LocalDate,
    val dayOfMonth: Int,
    val isSelected: Boolean,
    val hasMemo: Boolean
)
