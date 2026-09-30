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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.screen.MemoEditScreen
import com.lambdarc.litememo.ui.state.MemoEditUiResult
import com.lambdarc.litememo.ui.util.launchShareMemo
import com.lambdarc.litememo.ui.viewmodel.MemoEditViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

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
    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        viewModel.attachImages(uris.map { it.toString() })
    }

    MemoEditUiResultEffect(
        results = viewModel.uiResults,
        onResult = { result ->
            when (result) {
                is MemoEditUiResult.NavigateBack -> onNavigateBack()
                is MemoEditUiResult.MemoDeleted -> onMemoDelete(result.memoId)
                is MemoEditUiResult.SaveFailed -> onSaveError()
                is MemoEditUiResult.DeleteFailed -> onDeleteError()
                is MemoEditUiResult.ImageAttachFailed -> onImageAttachError()
            }
        },
        onConsumeResult = viewModel::onUiResultConsumed
    )

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

@Composable
internal fun MemoEditUiResultEffect(
    results: StateFlow<List<MemoEditUiResult>>,
    onResult: (MemoEditUiResult) -> Unit,
    onConsumeResult: (Long) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnConsumeResult by rememberUpdatedState(onConsumeResult)

    LaunchedEffect(results, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            results
                .map { it.firstOrNull() }
                .distinctUntilChangedBy { it?.id }
                .filterNotNull()
                .collect { result ->
                    currentOnResult(result)
                    currentOnConsumeResult(result.id)
                }
        }
    }
}
