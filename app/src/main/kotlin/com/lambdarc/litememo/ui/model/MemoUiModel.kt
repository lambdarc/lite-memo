package com.lambdarc.litememo.ui.model

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName

data class MemoUiModel(
    val id: MemoId,
    val title: String,
    val body: String,
    val tags: List<TagUiModel>,
    val updatedAtMillis: Long,
    val isFavorite: Boolean,
    val thumbnailPath: String? = null
) {
    companion object {
        fun fromDomain(
            memos: List<Memo>,
            tags: List<Tag>,
            resolveImagePath: (MemoImageFileName) -> String
        ): List<MemoUiModel> {
            val tagsById = tags.associateBy { it.id }
            return memos.map { memo ->
                MemoUiModel(
                    id = memo.id,
                    title = memo.title.value,
                    body = memo.body.value,
                    tags = memo.tagIds.mapNotNull { id ->
                        tagsById[id]?.let { TagUiModel.fromDomain(it) }
                    },
                    updatedAtMillis = memo.updatedAt.value,
                    isFavorite = memo.isFavorite,
                    thumbnailPath = memo.images.firstOrNull()?.let { image ->
                        resolveImagePath(image.fileName)
                    }
                )
            }
        }
    }
}
