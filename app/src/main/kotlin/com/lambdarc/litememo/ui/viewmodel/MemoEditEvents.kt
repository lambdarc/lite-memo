package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.model.value.MemoId

sealed interface MemoEditNavigationUiEvent {
    data object NavigateBack : MemoEditNavigationUiEvent
    data class MemoDeleted(val memoId: MemoId) : MemoEditNavigationUiEvent
}

sealed interface MemoEditOperationErrorUiEvent {
    data object SaveFailed : MemoEditOperationErrorUiEvent
    data object DeleteFailed : MemoEditOperationErrorUiEvent
    data object ImageAttachFailed : MemoEditOperationErrorUiEvent
}
