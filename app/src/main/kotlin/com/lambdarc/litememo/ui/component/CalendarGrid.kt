package com.lambdarc.litememo.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.state.CalendarDayUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val CALENDAR_GRID_SLIDE_DURATION_MILLIS = 220
private const val CALENDAR_GRID_FADE_IN_DURATION_MILLIS = 160
private const val CALENDAR_GRID_FADE_OUT_DURATION_MILLIS = 120

@Composable
fun AnimatedCalendarGrid(
    month: YearMonth?,
    days: List<CalendarDayUiState>,
    onDateSelect: (LocalDate) -> Unit
) {
    AnimatedContent(
        targetState = CalendarGridAnimationUiState(
            month = month,
            days = days
        ),
        contentKey = { it.month },
        transitionSpec = {
            val direction = if (targetState.month.isAfter(initialState.month)) 1 else -1
            (
                slideInHorizontally(
                    animationSpec = tween(CALENDAR_GRID_SLIDE_DURATION_MILLIS),
                    initialOffsetX = { width -> width / 4 * direction }
                ) + fadeIn(animationSpec = tween(CALENDAR_GRID_FADE_IN_DURATION_MILLIS))
                ).togetherWith(
                slideOutHorizontally(
                    animationSpec = tween(CALENDAR_GRID_SLIDE_DURATION_MILLIS),
                    targetOffsetX = { width -> -width / 4 * direction }
                ) + fadeOut(animationSpec = tween(CALENDAR_GRID_FADE_OUT_DURATION_MILLIS))
            ).using(SizeTransform(clip = false))
        },
        label = "calendar-month-grid"
    ) { state ->
        CalendarGrid(
            days = state.days,
            onDateSelect = onDateSelect
        )
    }
}

@Composable
private fun CalendarGrid(days: List<CalendarDayUiState>, onDateSelect: (LocalDate) -> Unit) {
    val leadingBlankCount = days.firstOrNull()?.date?.dayOfWeek?.value?.rem(DAY_COUNT) ?: 0
    val cells = List(leadingBlankCount) { null } + days

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CalendarWeekHeader()
        cells.chunked(DAY_COUNT).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(DAY_COUNT) { index ->
                    val day = week.getOrNull(index)
                    CalendarDayCell(
                        day = day,
                        onDateSelect = onDateSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarWeekHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        stringArrayResource(R.array.week_day_labels).forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUiState?,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    if (day == null) {
        Spacer(modifier = modifier.aspectRatio(1f))
        return
    }

    val containerColor = if (day.isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = if (day.isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val datePattern = stringResource(R.string.calendar_day_cell_date_format)
    val hasMemoDescription = stringResource(R.string.calendar_day_has_memo_description)
    val dateText = remember(datePattern, day.date) {
        day.date.format(DateTimeFormatter.ofPattern(datePattern))
    }
    val cellDescription = if (day.hasMemo) {
        stringResource(
            R.string.calendar_day_cell_description_with_memo,
            dateText,
            hasMemoDescription
        )
    } else {
        dateText
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(role = Role.Button) { onDateSelect(day.date) }
            .semantics(mergeDescendants = true) {
                contentDescription = cellDescription
                selected = day.isSelected
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (day.hasMemo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(contentColor)
            )
        }
    }
}

private const val DAY_COUNT = 7

private fun YearMonth?.isAfter(other: YearMonth?): Boolean {
    if (this == null || other == null) return true
    return this > other
}

private data class CalendarGridAnimationUiState(
    val month: YearMonth?,
    val days: List<CalendarDayUiState>
)
