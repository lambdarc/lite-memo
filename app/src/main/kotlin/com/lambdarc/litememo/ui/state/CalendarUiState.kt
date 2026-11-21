package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.MemoUiModel
import java.time.LocalDate
import java.time.YearMonth

sealed class CalendarUiState {

    data object Loading : CalendarUiState()

    data class Error(
        val selectedMonth: YearMonth? = null,
        val selectedDate: LocalDate? = null,
        val isCalendarExpanded: Boolean = true,
        val search: SearchUiState = SearchUiState()
    ) : CalendarUiState()

    data class Content(
        val selectedMonth: YearMonth? = null,
        val selectedDate: LocalDate? = null,
        val isCalendarExpanded: Boolean = true,
        val isDatePickerVisible: Boolean = false,
        val search: SearchUiState = SearchUiState(),
        val days: List<CalendarDayUiState> = emptyList(),
        val memos: List<MemoUiModel> = emptyList()
    ) : CalendarUiState()

}

data class CalendarDayUiState(
    val date: LocalDate,
    val dayOfMonth: Int,
    val isSelected: Boolean,
    val hasMemo: Boolean
)
