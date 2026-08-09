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
import com.lambdarc.litememo.ui.screen.TagManageScreen
import com.lambdarc.litememo.ui.viewmodel.TagManageViewModel

@Composable
fun TagManageRoute(
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: TagManageViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deleteErrorMessage = stringResource(R.string.tag_delete_error_body)

    LaunchedEffect(viewModel, snackbarHostState, deleteErrorMessage) {
        viewModel.deleteErrorEvent.collect {
            snackbarHostState.showSnackbar(
                message = deleteErrorMessage,
                withDismissAction = true
            )
        }
    }

    TagManageScreen(
        uiState = uiState,
        onBackClick = onNavigateBack,
        onCreateClick = { viewModel.startCreate() },
        onEditClick = { viewModel.startEdit(it) },
        onDeleteRequest = { viewModel.requestDelete(it) },
        onConfirmDelete = { viewModel.confirmDelete() },
        onDismissDelete = { viewModel.dismissDeleteDialog() },
        onEditNameChange = { viewModel.updateEditName(it) },
        onEditColorSelect = { viewModel.selectEditColor(it) },
        onSaveEdit = { viewModel.saveEdit() },
        onCancelEdit = { viewModel.cancelEdit() },
        onRetry = { viewModel.retry() },
        modifier = modifier
    )
}
