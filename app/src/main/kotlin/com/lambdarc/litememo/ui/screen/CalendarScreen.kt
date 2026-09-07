package com.lambdarc.litememo.ui.screen

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.component.AnimatedCalendarGrid
import com.lambdarc.litememo.ui.component.ErrorContent
import com.lambdarc.litememo.ui.component.LoadingContent
import com.lambdarc.litememo.ui.component.MemoCard
import com.lambdarc.litememo.ui.component.MessageContent
import com.lambdarc.litememo.ui.component.SearchTopBar
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.CalendarDayUiState
import com.lambdarc.litememo.ui.state.CalendarUiState
import com.lambdarc.litememo.ui.theme.LiteMemoTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MONTH_SWIPE_THRESHOLD_DP = 72
private const val CALENDAR_EXPAND_DURATION_MILLIS = 220
private const val CALENDAR_FADE_IN_DURATION_MILLIS = 160
private const val CALENDAR_SHRINK_DURATION_MILLIS = 180
private const val CALENDAR_FADE_OUT_DURATION_MILLIS = 120

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onCalendarExpandedToggle: () -> Unit,
    onDatePickerRequest: () -> Unit,
    onDatePickerDismiss: () -> Unit,
    onDatePick: (Long) -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onMemoClick: (MemoId) -> Unit,
    onCreateMemoClick: () -> Unit,
    modifier: Modifier = Modifier,
    canCreateMemo: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            uiState.isLoading -> LoadingContent()

            uiState.hasError -> ErrorContent(onRetry = onRetry)

            else -> Box(modifier = Modifier.fillMaxSize()) {
                CalendarContent(
                    uiState = uiState,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onDateSelect = onDateSelect,
                    onCalendarExpandedToggle = onCalendarExpandedToggle,
                    onDatePickerRequest = onDatePickerRequest,
                    onSearchToggle = onSearchToggle,
                    onSearchQueryChange = onSearchQueryChange,
                    onMemoClick = onMemoClick
                )
                if (canCreateMemo) {
                    FloatingActionButton(
                        onClick = onCreateMemoClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.create_memo)
                        )
                    }
                }
            }
        }

        if (uiState.isDatePickerVisible && uiState.selectedDate != null) {
            CalendarDatePickerDialog(
                selectedDate = uiState.selectedDate,
                onDatePick = onDatePick,
                onDismiss = onDatePickerDismiss
            )
        }
    }
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onCalendarExpandedToggle: () -> Unit,
    onDatePickerRequest: () -> Unit,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMemoClick: (MemoId) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SearchTopBar(
                search = uiState.search,
                onSearchToggle = onSearchToggle,
                onSearchQueryChange = onSearchQueryChange,
                inactiveTitle = stringResource(R.string.calendar_title)
            )
        }
        if (uiState.search.isActive) {
            if (uiState.search.hasError) {
                item {
                    MessageContent(
                        title = stringResource(R.string.search_error_title),
                        body = stringResource(R.string.search_error_body)
                    )
                }
            } else if (uiState.search.query.isBlank()) {
                item {
                    MessageContent(
                        title = stringResource(R.string.search_hint),
                        body = stringResource(R.string.search_hint_body)
                    )
                }
            } else if (uiState.search.results.isEmpty()) {
                item {
                    MessageContent(
                        title = stringResource(R.string.no_search_results_title),
                        body = stringResource(R.string.no_search_results_body)
                    )
                }
            } else {
                items(
                    items = uiState.search.results,
                    key = { memo -> memo.id.value }
                ) { memo ->
                    MemoCard(memo = memo, onClick = { onMemoClick(memo.id) })
                }
            }
        } else {
            item {
                CalendarMonthCard(
                    uiState = uiState,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onDateSelect = onDateSelect,
                    onCalendarExpandedToggle = onCalendarExpandedToggle,
                    onDatePickerRequest = onDatePickerRequest
                )
            }
            item {
                Text(
                    text = selectedDateTitle(uiState.selectedDate),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (uiState.memos.isEmpty()) {
                item {
                    MessageContent(
                        title = stringResource(R.string.empty_calendar_title),
                        body = stringResource(R.string.empty_calendar_body)
                    )
                }
            } else {
                items(
                    items = uiState.memos,
                    key = { memo -> memo.id.value }
                ) { memo ->
                    MemoCard(memo = memo, onClick = { onMemoClick(memo.id) })
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthCard(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onCalendarExpandedToggle: () -> Unit,
    onDatePickerRequest: () -> Unit
) {
    val swipeThresholdPx = with(LocalDensity.current) { MONTH_SWIPE_THRESHOLD_DP.dp.toPx() }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = calendarContainerColor()),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            CalendarMonthHeader(
                month = uiState.selectedMonth,
                isExpanded = uiState.isCalendarExpanded,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCalendarExpandedToggle = onCalendarExpandedToggle,
                onDatePickerRequest = onDatePickerRequest
            )
            AnimatedVisibility(
                visible = uiState.isCalendarExpanded,
                enter = expandVertically(
                    animationSpec = tween(CALENDAR_EXPAND_DURATION_MILLIS)
                ) + fadeIn(
                    animationSpec = tween(CALENDAR_FADE_IN_DURATION_MILLIS)
                ),
                exit = shrinkVertically(
                    animationSpec = tween(CALENDAR_SHRINK_DURATION_MILLIS)
                ) + fadeOut(
                    animationSpec = tween(CALENDAR_FADE_OUT_DURATION_MILLIS)
                )
            ) {
                Column(
                    modifier = Modifier
                        .pointerInput(swipeThresholdPx) {
                            var dragAmount = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragAmount = 0f },
                                onHorizontalDrag = { _, amount -> dragAmount += amount },
                                onDragEnd = {
                                    when (resolveMonthSwipe(dragAmount, swipeThresholdPx)) {
                                        MonthSwipeUiDirection.NEXT -> onNextMonth()
                                        MonthSwipeUiDirection.PREVIOUS -> onPreviousMonth()
                                        null -> Unit
                                    }
                                    dragAmount = 0f
                                },
                                onDragCancel = { dragAmount = 0f }
                            )
                        }
                        .animateContentSize(
                            animationSpec = tween(CALENDAR_EXPAND_DURATION_MILLIS)
                        )
                ) {
                    Spacer(modifier = Modifier.height(18.dp))
                    AnimatedCalendarGrid(
                        month = uiState.selectedMonth,
                        days = uiState.days,
                        onDateSelect = onDateSelect
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthHeader(
    month: YearMonth?,
    isExpanded: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCalendarExpandedToggle: () -> Unit,
    onDatePickerRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = monthTitle(month),
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onDatePickerRequest),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_month)
            )
        }
        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_month)
            )
        }
        IconButton(onClick = onCalendarExpandedToggle) {
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = stringResource(R.string.toggle_calendar)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDatePickerDialog(
    selectedDate: LocalDate,
    onDatePick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedDateMillis = remember(selectedDate) {
        selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    datePickerState.selectedDateMillis?.let(onDatePick) ?: onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.ok_label))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel_label))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
private fun calendarContainerColor(): Color {
    if (isSystemInDarkTheme()) {
        return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    }

    return MaterialTheme.colorScheme.surface
}

@Composable
private fun selectedDateTitle(date: LocalDate?): String {
    val pattern = stringResource(R.string.selected_date_format)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    return date?.let { selectedDate ->
        stringResource(
            R.string.selected_date_title_format,
            selectedDate.format(formatter)
        )
    } ?: ""
}

@Composable
private fun monthTitle(month: YearMonth?): String {
    val pattern = stringResource(R.string.month_title_format)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern) }
    return month?.format(formatter) ?: ""
}

@Preview(showBackground = true)
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CalendarScreenPreview() {
    val month = YearMonth.of(2026, 5)
    val selectedDate = LocalDate.of(2026, 5, 15)
    val uiState = CalendarUiState(
        isLoading = false,
        selectedMonth = month,
        selectedDate = selectedDate,
        days = (1..month.lengthOfMonth()).map { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            CalendarDayUiState(
                date = date,
                dayOfMonth = dayOfMonth,
                isSelected = date == selectedDate,
                hasMemo = dayOfMonth in listOf(9, 11, 14, 15, 19, 28)
            )
        },
        memos = listOf(
            MemoUiModel(
                id = MemoId("memo-1"),
                title = "週次レビュー",
                body = "完了したタスクと来週の優先度を整理する。",
                tags = listOf(TagUiModel(TagId("tag-work"), "仕事", 0xFF6750A4)),
                updatedAtMillis = System.currentTimeMillis(),
                isFavorite = false
            ),
            MemoUiModel(
                id = MemoId("memo-2"),
                title = "献立メモ",
                body = "冷蔵庫の野菜を使い切る。買い足しは卵。",
                tags = listOf(TagUiModel(TagId("tag-life"), "生活", 0xFF006D3B)),
                updatedAtMillis = System.currentTimeMillis(),
                isFavorite = false
            )
        )
    )

    LiteMemoTheme {
        CalendarScreen(
            uiState = uiState,
            onPreviousMonth = {},
            onNextMonth = {},
            onDateSelect = {},
            onCalendarExpandedToggle = {},
            onDatePickerRequest = {},
            onDatePickerDismiss = {},
            onDatePick = {},
            onSearchToggle = {},
            onSearchQueryChange = {},
            onRetry = {},
            onMemoClick = {},
            onCreateMemoClick = {}
        )
    }
}
