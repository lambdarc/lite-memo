package com.lambdarc.litememo.ui.route

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.screen.TrashScreen
import com.lambdarc.litememo.ui.screen.TrashScreenActions
import com.lambdarc.litememo.ui.viewmodel.TrashViewModel

@Composable
fun TrashRoute(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionErrorMessage = stringResource(R.string.trash_action_error_body)

    LaunchedEffect(viewModel, snackbarHostState, actionErrorMessage) {
        viewModel.actionErrorEvent.collect {
            snackbarHostState.showSnackbar(
                message = actionErrorMessage,
                withDismissAction = true
            )
        }
    }

    TrashScreen(
        uiState = uiState,
        actions = object : TrashScreenActions {
            override fun onBackClick() = onNavigateBack()

            override fun onMemoLongClick(memoId: MemoId) {
                viewModel.startSelection(memoId)
            }

            override fun onMemoSelectionToggle(memoId: MemoId) {
                viewModel.toggleMemoSelection(memoId)
            }

            override fun onClearSelection() = viewModel.clearSelection()

            override fun onRestoreSelectedMemos() = viewModel.restoreSelectedMemos()

            override fun onEmptyTrashRequest() = viewModel.requestEmptyTrash()

            override fun onConfirmEmptyTrash() = viewModel.confirmEmptyTrash()

            override fun onDismissEmptyTrash() = viewModel.dismissEmptyTrashDialog()

            override fun onRetry() = viewModel.retry()
        },
        modifier = modifier
    )
}
