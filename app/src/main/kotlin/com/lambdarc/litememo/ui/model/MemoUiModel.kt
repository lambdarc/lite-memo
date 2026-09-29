package com.lambdarc.litememo.ui.model

import com.lambdarc.litememo.domain.model.value.MemoId

data class MemoUiModel(
    val id: MemoId,
    val title: String,
    val body: String,
    val tags: List<TagUiModel>,
    val updatedAtMillis: Long,
    val isFavorite: Boolean,
    val thumbnailPath: String? = null
)
