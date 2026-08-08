package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis

data class Memo(
    val id: MemoId,
    val title: MemoTitle,
    val body: MemoBody,
    val createdAt: TimestampMillis,
    val updatedAt: TimestampMillis,
    val tagIds: List<TagId> = emptyList(),
    val images: List<MemoImage> = emptyList(),
    val isFavorite: Boolean = false,
    val deletedAt: TimestampMillis? = null
) {

    init {
        require(updatedAt.value >= createdAt.value) {
            "Memo updatedAt must be greater than or equal to createdAt."
        }
        require(deletedAt == null || deletedAt.value >= updatedAt.value) {
            "Memo deletedAt must be greater than or equal to updatedAt."
        }
        require(images.distinctBy { it.id }.size == images.size) {
            "Memo images must not contain duplicated ids."
        }
    }

}

fun Memo.updatedAtFrom(now: TimestampMillis): TimestampMillis =
    TimestampMillis(maxOf(now.value, updatedAt.value, createdAt.value))
