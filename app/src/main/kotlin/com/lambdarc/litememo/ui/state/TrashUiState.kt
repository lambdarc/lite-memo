package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.TrashedMemoUiModel

data class TrashUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val memos: List<TrashedMemoUiModel> = emptyList(),
    val selection: MemoSelectionUiState = MemoSelectionUiState(),
    val showEmptyTrashDialog: Boolean = false
)
