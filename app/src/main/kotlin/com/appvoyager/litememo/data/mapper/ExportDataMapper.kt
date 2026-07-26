package com.appvoyager.litememo.data.mapper

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import com.appvoyager.litememo.data.model.export.TagExportDto
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoImage
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.MemoBody
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.MemoImageFileName
import com.appvoyager.litememo.domain.model.value.MemoImageId
import com.appvoyager.litememo.domain.model.value.MemoTitle
import com.appvoyager.litememo.domain.model.value.TagColor
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis

fun LiteMemoExportDto.toDomain() = ExportData(
    version = version,
    exportedAt = TimestampMillis(exportedAt),
    tags = tags.map { it.toDomain() },
    memos = memos.map { it.toDomain() }
)

fun LiteMemoExportDto.toDomain(imageFileNames: Map<String, MemoImageFileName>) = ExportData(
    version = version,
    exportedAt = TimestampMillis(exportedAt),
    tags = tags.map { it.toDomain() },
    memos = memos.map { it.toDomain(imageFileNames) }
)

fun MemoExportDto.toDomain(imageFileNames: Map<String, MemoImageFileName>) = toDomain().copy(
    images = images.map { image ->
        MemoImage(
            id = MemoImageId(image.id),
            fileName = requireNotNull(imageFileNames[image.id]) {
                "Imported image file name is missing for an image the manifest declares."
            }
        )
    }
)

fun Memo.toExportDto(images: List<MemoImageExportDto>) = MemoExportDto(
    id = id.value,
    title = title.value,
    body = body.value,
    createdAt = createdAt.value,
    updatedAt = updatedAt.value,
    isFavorite = isFavorite,
    tagIds = tagIds.map { it.value },
    images = images
)

fun MemoExportDto.toDomain() = Memo(
    id = MemoId(id),
    title = MemoTitle(title),
    body = MemoBody(body),
    createdAt = TimestampMillis(createdAt),
    updatedAt = TimestampMillis(updatedAt),
    isFavorite = isFavorite,
    tagIds = tagIds.map { TagId(it) },
    deletedAt = null
)

fun Tag.toExportDto() = TagExportDto(
    id = id.value,
    name = name.value,
    colorArgb = color.argb,
    createdAt = createdAt.value
)

fun TagExportDto.toDomain() = Tag(
    id = TagId(id),
    name = TagName(name),
    color = TagColor(colorArgb),
    createdAt = TimestampMillis(createdAt)
)
