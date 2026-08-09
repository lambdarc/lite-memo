package com.lambdarc.litememo.ui.model

import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.MemoImageFileName

data class MemoImageUiModel(
    val id: String,
    val fileName: String,
    val filePath: String,
    val isPersisted: Boolean
) {
    companion object {
        fun fromDomain(
            image: MemoImage,
            resolveImagePath: (MemoImageFileName) -> String,
            isPersisted: Boolean
        ): MemoImageUiModel = MemoImageUiModel(
            id = image.id.value,
            fileName = image.fileName.value,
            filePath = resolveImagePath(image.fileName),
            isPersisted = isPersisted
        )
    }
}
