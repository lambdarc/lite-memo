package com.lambdarc.litememo.domain.repository

import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoImageFileName

interface MemoImageStore {

    suspend fun saveImage(source: ImageSourceReference): MemoImage

    suspend fun deleteImages(fileNames: List<MemoImageFileName>)

    fun resolveImagePath(fileName: MemoImageFileName): String
}
