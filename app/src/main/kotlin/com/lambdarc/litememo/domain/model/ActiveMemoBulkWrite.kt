package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoId

sealed interface ActiveMemoBulkWrite {

    val expectedMemo: Memo
    val memoId: MemoId get() = expectedMemo.id

    data class Update(override val expectedMemo: Memo, val updatedMemo: Memo) :
        ActiveMemoBulkWrite {
        init {
            require(expectedMemo.id == updatedMemo.id) { "Memo identity must not change." }
        }
    }

    data class CheckOnly(override val expectedMemo: Memo) : ActiveMemoBulkWrite

}
