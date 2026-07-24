package com.appvoyager.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.usecase.DeleteMemosPermanentlyUseCase
import com.appvoyager.litememo.domain.usecase.ObserveTagsUseCase
import com.appvoyager.litememo.domain.usecase.ObserveTrashedMemosUseCase
import com.appvoyager.litememo.domain.usecase.PurgeExpiredTrashedMemosUseCase
import com.appvoyager.litememo.domain.usecase.RestoreMemosFromTrashUseCase
import com.appvoyager.litememo.ui.model.TagUiModel
import com.appvoyager.litememo.ui.model.TrashedMemoUiModel
import com.appvoyager.litememo.ui.state.TrashSelectionUiState
import com.appvoyager.litememo.ui.state.TrashUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrashViewModel @Inject constructor(
    private val observeTrashedMemosUseCase: ObserveTrashedMemosUseCase,
    private val observeTagsUseCase: ObserveTagsUseCase,
    private val restoreMemosFromTrashUseCase: RestoreMemosFromTrashUseCase,
    private val deleteMemosPermanentlyUseCase: DeleteMemosPermanentlyUseCase,
    private val purgeExpiredTrashedMemosUseCase: PurgeExpiredTrashedMemosUseCase
) : ViewModel() {

    init {
        purgeExpiredTrashedMemos()
    }

    private val retryTrigger = MutableStateFlow(false)
    private val hasPurgeError = MutableStateFlow(false)
    private val selection = MutableStateFlow(TrashSelectionUiState())
    private val showEmptyTrashDialog = MutableStateFlow(false)
    private var isActionInFlight = false

    private val _actionErrorEvent = Channel<Unit>(Channel.CONFLATED)
    val actionErrorEvent = _actionErrorEvent.receiveAsFlow()

    private val observedData = retryTrigger.flatMapLatest {
        combine(
            observeTrashedMemosUseCase()
                .map<List<Memo>, List<Memo>?> { it }
                .catch { emit(null) },
            observeTagsUseCase()
                .map<List<Tag>, List<Tag>?> { it }
                .catch { emit(null) }
        ) { memos, tags ->
            ObservedTrashData(memos = memos, tags = tags)
        }
    }

    val uiState: StateFlow<TrashUiState> = combine(
        observedData,
        hasPurgeError,
        selection,
        showEmptyTrashDialog
    ) { observed, purgeError, activeSelection, showEmptyDialog ->
        val hasError = observed.memos == null || observed.tags == null || purgeError
        val uiMemos = if (!hasError) {
            toUiModels(
                memos = requireNotNull(observed.memos),
                tags = requireNotNull(observed.tags)
            )
        } else {
            emptyList()
        }
        val visibleMemoIds = uiMemos.map { it.id }.toSet()
        TrashUiState(
            isLoading = false,
            hasError = hasError,
            memos = uiMemos,
            selection = TrashSelectionUiState(
                selectedMemoIds = activeSelection.selectedMemoIds intersect visibleMemoIds
            ),
            showEmptyTrashDialog = showEmptyDialog
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TrashUiState()
    )

    fun startSelection(id: MemoId) {
        showEmptyTrashDialog.value = false
        selection.value = TrashSelectionUiState(selectedMemoIds = setOf(id))
    }

    fun toggleMemoSelection(id: MemoId) {
        val current = selection.value.selectedMemoIds
        val next = if (id in current) {
            current - id
        } else {
            current + id
        }
        selection.value = TrashSelectionUiState(selectedMemoIds = next)
    }

    fun clearSelection() {
        selection.value = TrashSelectionUiState()
    }

    fun restoreSelectedMemos() {
        val memoIds = uiState.value.selection.selectedMemoIds.toList()
        if (memoIds.isEmpty()) return
        if (isActionInFlight) return
        isActionInFlight = true

        viewModelScope.launch {
            try {
                restoreMemosFromTrashUseCase(memoIds)
                clearSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _actionErrorEvent.trySend(Unit)
            } finally {
                isActionInFlight = false
            }
        }
    }

    fun requestEmptyTrash() {
        if (uiState.value.memos.isEmpty()) return
        showEmptyTrashDialog.value = true
    }

    fun dismissEmptyTrashDialog() {
        showEmptyTrashDialog.value = false
    }

    fun confirmEmptyTrash() {
        val memoIds = uiState.value.memos.map { it.id }
        if (memoIds.isEmpty()) {
            showEmptyTrashDialog.value = false
            return
        }
        if (isActionInFlight) return
        isActionInFlight = true

        viewModelScope.launch {
            try {
                deleteMemosPermanentlyUseCase(memoIds)
                showEmptyTrashDialog.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                showEmptyTrashDialog.value = false
                _actionErrorEvent.trySend(Unit)
            } finally {
                isActionInFlight = false
            }
        }
    }

    fun retry() {
        hasPurgeError.value = false
        purgeExpiredTrashedMemos()
        retryTrigger.update { !it }
    }

    private fun purgeExpiredTrashedMemos() {
        viewModelScope.launch {
            try {
                purgeExpiredTrashedMemosUseCase()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                hasPurgeError.value = true
            }
        }
    }

    private fun toUiModels(memos: List<Memo>, tags: List<Tag>): List<TrashedMemoUiModel> {
        val tagsById = tags.associateBy { it.id }
        return memos.mapNotNull { memo ->
            val deletedAt = memo.deletedAt ?: return@mapNotNull null
            TrashedMemoUiModel(
                id = memo.id,
                title = memo.title.value,
                body = memo.body.value,
                tags = memo.tagIds.mapNotNull { id ->
                    tagsById[id]?.let { TagUiModel.fromDomain(it) }
                },
                deletedAt = deletedAt
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private data class ObservedTrashData(val memos: List<Memo>?, val tags: List<Tag>?)
