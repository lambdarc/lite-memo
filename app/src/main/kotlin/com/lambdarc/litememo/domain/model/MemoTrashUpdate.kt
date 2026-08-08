package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TimestampMillis

data class MemoTrashUpdate(val memoId: MemoId, val deletedAt: TimestampMillis)
