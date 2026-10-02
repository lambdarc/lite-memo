package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.TrashedMemoUiModel

data class TrashUiState(
    val status: ScreenUiStatus = ScreenUiStatus.LOADING,
    val memos: List<TrashedMemoUiModel> = emptyList(),
    val selection: MemoSelectionUiState = MemoSelectionUiState(),
    val showEmptyTrashDialog: Boolean = false
) {

    init {
        val isContent = status == ScreenUiStatus.CONTENT
        require(isContent || memos.isEmpty()) {
            "TrashUiState memos must be empty unless status is CONTENT."
        }
        require(isContent || !showEmptyTrashDialog) {
            "TrashUiState showEmptyTrashDialog must be false unless status is CONTENT."
        }
    }

}
