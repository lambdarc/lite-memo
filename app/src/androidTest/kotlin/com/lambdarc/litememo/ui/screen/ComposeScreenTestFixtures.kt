package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.Composable
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoImageUiModel
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.theme.LiteMemoTheme

@Composable
fun TestScreenContent(content: @Composable () -> Unit) {
    LiteMemoTheme {
        content()
    }
}

fun testMemoUiModel(
    id: String = "memo-1",
    title: String = "Title",
    body: String = "Body",
    tags: List<TagUiModel> = emptyList(),
    thumbnailPath: String? = null
) = MemoUiModel(
    id = MemoId(id),
    title = title,
    body = body,
    tags = tags,
    updatedAtMillis = 1_000L,
    isFavorite = false,
    thumbnailPath = thumbnailPath
)

fun testTagUiModel(id: String = "tag-1", name: String = "Tag") = TagUiModel(
    id = TagId(id),
    name = name,
    colorArgb = 0xFF4CAF50
)

fun testMemoImageUiModel(
    id: String = "image-1",
    fileName: String = "image-1.jpg",
    filePath: String = "/missing/image-1.jpg",
    isPersisted: Boolean = true
) = MemoImageUiModel(
    id = id,
    fileName = fileName,
    filePath = filePath,
    isPersisted = isPersisted
)
