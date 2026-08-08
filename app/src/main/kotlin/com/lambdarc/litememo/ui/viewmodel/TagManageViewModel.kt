package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lambdarc.litememo.domain.exception.DuplicateTagNameException
import com.lambdarc.litememo.domain.model.SaveTagCommand
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.usecase.DeleteTagUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.SaveTagUseCase
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.TagEditUiState
import com.lambdarc.litememo.ui.state.TagManageUiState
import dagger.hilt.android.lifecycle.HiltViewModel
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
class TagManageViewModel @Inject constructor(
    private val observeTagsUseCase: ObserveTagsUseCase,
    private val saveTagUseCase: SaveTagUseCase,
    private val deleteTagUseCase: DeleteTagUseCase
) : ViewModel() {

    private val editingTag = MutableStateFlow<TagEditUiState?>(null)
    private val deleteDialog = MutableStateFlow<TagUiModel?>(null)
    private val retryTrigger = MutableStateFlow(false)

    private val _deleteErrorEvent = Channel<Unit>(Channel.CONFLATED)
    val deleteErrorEvent = _deleteErrorEvent.receiveAsFlow()

    val uiState: StateFlow<TagManageUiState> = retryTrigger.flatMapLatest {
        combine(
            observeTagsUseCase()
                .map<List<Tag>, List<Tag>?> { it }
                .catch { emit(null) },
            editingTag,
            deleteDialog
        ) { tags, editing, deleting ->
            TagManageUiState(
                isLoading = false,
                hasError = tags == null,
                tags = tags?.map { TagUiModel.fromDomain(it) } ?: emptyList(),
                editingTag = editing,
                showDeleteDialog = deleting
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TagManageUiState()
    )

    fun startCreate() {
        editingTag.value = TagEditUiState()
    }

    fun startEdit(tagId: TagId) {
        val tag = uiState.value.tags.find { it.id == tagId } ?: return
        editingTag.value = TagEditUiState(
            id = tag.id,
            name = tag.name,
            colorArgb = tag.colorArgb
        )
    }

    fun updateEditName(name: String) {
        editingTag.update {
            it?.copy(name = name, nameError = false, duplicateNameError = false, saveError = false)
        }
    }

    fun selectEditColor(colorArgb: Long) {
        editingTag.update { it?.copy(colorArgb = colorArgb, saveError = false) }
    }

    fun saveEdit() {
        val state = editingTag.value ?: return
        if (state.isSaving) return
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            editingTag.update { it?.copy(nameError = true) }
            return
        }
        if (isDuplicateName(trimmedName, state.id)) {
            editingTag.update { it?.copy(duplicateNameError = true) }
            return
        }
        editingTag.update { it?.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                saveTagUseCase(
                    SaveTagCommand(
                        id = state.id,
                        name = TagName(trimmedName),
                        color = TagColor(state.colorArgb)
                    )
                )
            }.onSuccess {
                editingTag.value = null
            }.onFailure { error ->
                editingTag.update { current ->
                    when (error) {
                        is DuplicateTagNameException -> current?.copy(
                            duplicateNameError = true,
                            saveError = false,
                            isSaving = false
                        )

                        else -> current?.copy(saveError = true, isSaving = false)
                    }
                }
            }
        }
    }

    fun cancelEdit() {
        editingTag.value = null
    }

    fun requestDelete(tag: TagUiModel) {
        deleteDialog.value = tag
    }

    fun confirmDelete() {
        val tag = deleteDialog.value ?: return
        viewModelScope.launch {
            runCatching {
                deleteTagUseCase(tag.id)
            }.onSuccess {
                deleteDialog.value = null
            }.onFailure {
                deleteDialog.value = null
                _deleteErrorEvent.trySend(Unit)
            }
        }
    }

    fun dismissDeleteDialog() {
        deleteDialog.value = null
    }

    fun retry() {
        retryTrigger.update { !it }
    }

    private fun isDuplicateName(name: String, excludeId: TagId?): Boolean =
        uiState.value.tags.any { it.name == name && it.id != excludeId }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
