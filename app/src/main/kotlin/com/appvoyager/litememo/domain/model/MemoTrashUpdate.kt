package com.appvoyager.litememo.domain.model

import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.TimestampMillis

data class MemoTrashUpdate(val memoId: MemoId, val deletedAt: TimestampMillis)
