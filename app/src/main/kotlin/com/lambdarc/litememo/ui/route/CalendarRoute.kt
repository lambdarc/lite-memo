package com.lambdarc.litememo.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.screen.CalendarScreen
import com.lambdarc.litememo.ui.viewmodel.CalendarViewModel

@Composable
fun CalendarRoute(
    onMemoClick: (MemoId) -> Unit,
    onCreateMemoClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CalendarScreen(
        uiState = uiState,
        onPreviousMonth = { viewModel.previousMonth() },
        onNextMonth = { viewModel.nextMonth() },
        onDateSelect = { date -> viewModel.selectDate(date) },
        onCalendarExpandedToggle = { viewModel.toggleCalendarExpanded() },
        onDatePickerRequest = { viewModel.showDatePicker() },
        onDatePickerDismiss = { viewModel.dismissDatePicker() },
        onDatePick = { date -> viewModel.selectDateFromPicker(date) },
        onSearchToggle = { viewModel.toggleSearch() },
        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
        onRetry = { viewModel.retry() },
        onMemoClick = onMemoClick,
        onCreateMemoClick = {
            viewModel.selectedDateMillis().let(onCreateMemoClick)
        },
        modifier = modifier
    )
}
