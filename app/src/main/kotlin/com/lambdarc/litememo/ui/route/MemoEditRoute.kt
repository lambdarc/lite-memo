package com.lambdarc.litememo.ui.route

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.screen.MemoEditScreen
import com.lambdarc.litememo.ui.util.launchShareMemo
import com.lambdarc.litememo.ui.viewmodel.MemoEditNavigationUiEvent
import com.lambdarc.litememo.ui.viewmodel.MemoEditOperationErrorUiEvent
import com.lambdarc.litememo.ui.viewmodel.MemoEditViewModel

@Composable
fun MemoEditRoute(
    onNavigateBack: () -> Unit,
    onMemoDelete: (MemoId) -> Unit,
    onSaveError: () -> Unit,
    onDeleteError: () -> Unit,
    onShareError: () -> Unit,
    onImageAttachError: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnMemoDelete by rememberUpdatedState(onMemoDelete)
    val currentOnSaveError by rememberUpdatedState(onSaveError)
    val currentOnDeleteError by rememberUpdatedState(onDeleteError)
    val currentOnImageAttachError by rememberUpdatedState(onImageAttachError)
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.attachImages(uris.map { it.toString() })
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                MemoEditNavigationUiEvent.NavigateBack -> currentOnNavigateBack()
                is MemoEditNavigationUiEvent.MemoDeleted -> currentOnMemoDelete(event.memoId)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.operationErrorEvent.collect { event ->
            when (event) {
                MemoEditOperationErrorUiEvent.SaveFailed -> currentOnSaveError()
                MemoEditOperationErrorUiEvent.DeleteFailed -> currentOnDeleteError()
                MemoEditOperationErrorUiEvent.ImageAttachFailed -> currentOnImageAttachError()
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.flushEdits()
    }

    BackHandler(enabled = !uiState.isDeletePending) {
        viewModel.finishEditing()
    }

    MemoEditScreen(
        uiState = uiState,
        onTitleChange = { viewModel.updateTitle(it) },
        onBodyChange = { viewModel.updateBody(it) },
        onTagToggle = { viewModel.toggleTag(it) },
        onDelete = { viewModel.delete() },
        onBackRequest = { viewModel.finishEditing() },
        onRetry = { viewModel.reload() },
        onRetryTags = { viewModel.retryTags() },
        onAttachImageRequest = {
            pickImagesLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onImageRemove = { viewModel.removeImage(it) },
        onShareMemo = {
            val text = viewModel.formatMemoText() ?: return@MemoEditScreen
            context.launchShareMemo(
                text = text,
                subject = uiState.title.trim().ifEmpty { null },
                onError = onShareError
            )
        },
        modifier = modifier
    )
}
