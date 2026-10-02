package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.model.MemoUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CalendarUiStateTest {

    @Test
    fun normalContentKeepsDaysAndMemos() {
        // Arrange
        val days = listOf(calendarDay())
        val memos = listOf(memoUiModel())

        // Act
        // Normal: content status keeps its days and memos.
        val state = CalendarUiState(status = ScreenUiStatus.CONTENT, days = days, memos = memos)

        // Assert
        assertEquals(days, state.days)
    }

    @Test
    fun errorConstructorThrowsWhenDaysRemainOutsideContent() {
        // Act & Assert
        // Error: days cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            CalendarUiState(status = ScreenUiStatus.ERROR, days = listOf(calendarDay()))
        }
    }

    @Test
    fun errorConstructorThrowsWhenMemosRemainOutsideContent() {
        // Act & Assert
        // Error: memos cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            CalendarUiState(status = ScreenUiStatus.LOADING, memos = listOf(memoUiModel()))
        }
    }

    private fun calendarDay() = CalendarDayUiState(
        date = LocalDate.of(2026, 5, 11),
        dayOfMonth = 11,
        isSelected = false,
        hasMemo = true
    )

    private fun memoUiModel() = MemoUiModel(
        id = MemoId("memo-1"),
        title = "Shopping",
        body = "Buy coffee",
        tags = emptyList(),
        updatedAtMillis = 1_000L,
        isFavorite = false
    )

}
