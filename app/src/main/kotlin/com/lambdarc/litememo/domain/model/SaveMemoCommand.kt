package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis

data class SaveMemoCommand(
    val id: MemoId? = null,
    val title: MemoTitle,
    val body: MemoBody,
    val createdAt: TimestampMillis? = null,
    val tagIds: List<TagId> = emptyList(),
    val images: List<MemoImage> = emptyList(),
    val isFavorite: Boolean = false
)
