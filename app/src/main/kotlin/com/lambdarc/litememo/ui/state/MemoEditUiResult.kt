package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId

sealed interface MemoEditUiResult {
    val id: Long

    data class NavigateBack(override val id: Long) : MemoEditUiResult
    data class MemoDeleted(override val id: Long, val memoId: MemoId) : MemoEditUiResult
    data class SaveFailed(override val id: Long) : MemoEditUiResult
    data class DeleteFailed(override val id: Long) : MemoEditUiResult
    data class ImageAttachFailed(override val id: Long) : MemoEditUiResult
}
