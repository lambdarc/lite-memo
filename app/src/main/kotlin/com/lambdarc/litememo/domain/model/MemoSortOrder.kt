package com.lambdarc.litememo.domain.model

enum class MemoSortOrder {
    UPDATED_NEWEST,
    CREATED_NEWEST
}

fun List<Memo>.sortedBy(order: MemoSortOrder): List<Memo> = when (order) {
    MemoSortOrder.UPDATED_NEWEST -> sortedWith(
        compareByDescending<Memo> { it.isFavorite }
            .thenByDescending { it.updatedAt.value }
            .thenByDescending { it.createdAt.value }
    )

    MemoSortOrder.CREATED_NEWEST -> sortedWith(
        compareByDescending<Memo> { it.isFavorite }
            .thenByDescending { it.createdAt.value }
            .thenByDescending { it.updatedAt.value }
    )
}
