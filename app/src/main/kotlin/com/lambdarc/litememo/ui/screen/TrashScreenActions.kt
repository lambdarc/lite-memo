package com.lambdarc.litememo.ui.screen

import com.lambdarc.litememo.domain.model.value.MemoId

interface TrashScreenActions {
    fun onBackClick()

    fun onMemoLongClick(memoId: MemoId)

    fun onMemoSelectionToggle(memoId: MemoId)

    fun onClearSelection()

    fun onRestoreSelectedMemos()

    fun onEmptyTrashRequest()

    fun onConfirmEmptyTrash()

    fun onDismissEmptyTrash()

    fun onRetry()
}
