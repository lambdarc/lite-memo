package com.lambdarc.litememo.data.util

import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.repository.MemoImageStore

suspend fun MemoImageStore.deleteImageFiles(fileNames: Collection<String>) {
    if (fileNames.isEmpty()) return
    deleteImages(fileNames.map { MemoImageFileName(it) })
}
