package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId

data class MemoSelectionUiState(val selectedMemoIds: Set<MemoId> = emptySet()) {

    val isActive: Boolean
        get() = selectedMemoIds.isNotEmpty()

    val selectedCount: Int
        get() = selectedMemoIds.size

    fun contains(memoId: MemoId): Boolean = memoId in selectedMemoIds

    fun selectOnly(memoId: MemoId): MemoSelectionUiState = MemoSelectionUiState(setOf(memoId))

    fun toggle(memoId: MemoId): MemoSelectionUiState = MemoSelectionUiState(
        if (contains(memoId)) selectedMemoIds - memoId else selectedMemoIds + memoId
    )

    fun clear(): MemoSelectionUiState = MemoSelectionUiState()

}
