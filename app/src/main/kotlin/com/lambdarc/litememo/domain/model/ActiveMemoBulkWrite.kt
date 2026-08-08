package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TimestampMillis

sealed interface ActiveMemoBulkWrite {

    val memoId: MemoId
    val expectedUpdatedAt: TimestampMillis

    data class Update(
        override val memoId: MemoId,
        override val expectedUpdatedAt: TimestampMillis,
        val updatedMemo: Memo
    ) : ActiveMemoBulkWrite

    data class CheckOnly(
        override val memoId: MemoId,
        override val expectedUpdatedAt: TimestampMillis
    ) : ActiveMemoBulkWrite

}
