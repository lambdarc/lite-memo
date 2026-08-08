package com.lambdarc.litememo.data.local.model

data class MemoSummaryProjection(
    val id: String,
    val title: String,
    val body: String,
    val isFavorite: Boolean
)
