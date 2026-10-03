package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.TrashedMemoUiModel

sealed class TrashUiState {

    data object Loading : TrashUiState()

    data class Error(val selection: MemoSelectionUiState = MemoSelectionUiState()) : TrashUiState()

    data class Content(
        val memos: List<TrashedMemoUiModel> = emptyList(),
        val selection: MemoSelectionUiState = MemoSelectionUiState(),
        val showEmptyTrashDialog: Boolean = false
    ) : TrashUiState()

}
