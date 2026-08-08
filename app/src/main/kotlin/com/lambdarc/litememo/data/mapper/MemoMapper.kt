package com.lambdarc.litememo.data.mapper

import com.lambdarc.litememo.data.local.entity.MemoEntity
import com.lambdarc.litememo.data.local.entity.MemoImageEntity
import com.lambdarc.litememo.data.local.entity.MemoTagRefEntity
import com.lambdarc.litememo.data.local.model.MemoSummaryProjection
import com.lambdarc.litememo.data.local.model.MemoWithRefs
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis

fun Memo.toEntity() = MemoEntity(
    id = id.value,
    title = title.value,
    body = body.value,
    createdAt = createdAt.value,
    updatedAt = updatedAt.value,
    isFavorite = isFavorite,
    deletedAt = deletedAt?.value
)

fun Memo.toTagRefs() = tagIds.mapIndexed { index, tagId ->
    MemoTagRefEntity(
        memoId = id.value,
        tagId = tagId.value,
        position = index
    )
}

fun Memo.toImageRefs() = images.mapIndexed { index, image ->
    MemoImageEntity(
        id = image.id.value,
        memoId = id.value,
        fileName = image.fileName.value,
        position = index
    )
}

fun List<Memo>.toTagRefsByMemoId() = associate { memo -> memo.id.value to memo.toTagRefs() }

fun List<Memo>.toImageRefsByMemoId() = associate { memo -> memo.id.value to memo.toImageRefs() }

fun MemoEntity.toDomain(tagRefs: List<MemoTagRefEntity>, imageRefs: List<MemoImageEntity>): Memo {
    require(tagRefs.all { it.memoId == id }) {
        "All tagRefs must reference memoId=$id."
    }
    require(imageRefs.all { it.memoId == id }) {
        "All imageRefs must reference memoId=$id."
    }

    return Memo(
        id = MemoId(id),
        title = MemoTitle(title),
        body = MemoBody(body),
        createdAt = TimestampMillis(createdAt),
        updatedAt = TimestampMillis(updatedAt),
        tagIds = tagRefs.sortedBy { it.position }.map { TagId(it.tagId) },
        images = imageRefs.sortedBy { it.position }.map {
            MemoImage(
                id = MemoImageId(it.id),
                fileName = MemoImageFileName(it.fileName)
            )
        },
        isFavorite = isFavorite,
        deletedAt = deletedAt?.let { TimestampMillis(it) }
    )
}

fun MemoSummaryProjection.toDomain() = MemoSummary(
    id = MemoId(id),
    title = MemoTitle(title),
    body = MemoBody(body),
    isFavorite = isFavorite
)

fun MemoWithRefs.toDomain() = memo.toDomain(tagRefs, imageRefs)
