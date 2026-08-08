package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoTitle

data class MemoSummary(
    val id: MemoId,
    val title: MemoTitle,
    val body: MemoBody,
    val isFavorite: Boolean
)
