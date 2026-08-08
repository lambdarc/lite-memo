package com.lambdarc.litememo.ui.widget.data

import com.lambdarc.litememo.domain.model.value.MemoId

data class WidgetItem(
    val id: MemoId,
    val title: String,
    val snippet: String,
    val isFavorite: Boolean
)
