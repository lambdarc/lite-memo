package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.MemoUiModel
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val status: ScreenUiStatus = ScreenUiStatus.LOADING,
    val selectedMonth: YearMonth? = null,
    val selectedDate: LocalDate? = null,
    val isCalendarExpanded: Boolean = true,
    val isDatePickerVisible: Boolean = false,
    val search: SearchUiState = SearchUiState(),
    val days: List<CalendarDayUiState> = emptyList(),
    val memos: List<MemoUiModel> = emptyList()
) {

    init {
        val isContent = status == ScreenUiStatus.CONTENT
        require(isContent || days.isEmpty()) {
            "CalendarUiState days must be empty unless status is CONTENT."
        }
        require(isContent || memos.isEmpty()) {
            "CalendarUiState memos must be empty unless status is CONTENT."
        }
    }

}

data class CalendarDayUiState(
    val date: LocalDate,
    val dayOfMonth: Int,
    val isSelected: Boolean,
    val hasMemo: Boolean
)
