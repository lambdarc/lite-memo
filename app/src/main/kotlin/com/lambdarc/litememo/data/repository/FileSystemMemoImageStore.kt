package com.lambdarc.litememo.data.repository

import com.lambdarc.litememo.data.image.MemoImageFileDataSource
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.provider.MemoImageIdProvider
import com.lambdarc.litememo.domain.repository.MemoImageStore
import javax.inject.Inject

class FileSystemMemoImageStore @Inject constructor(
    private val dataSource: MemoImageFileDataSource,
    private val memoImageIdProvider: MemoImageIdProvider
) : MemoImageStore {

    override suspend fun saveImage(source: ImageSourceReference): MemoImage {
        val id = memoImageIdProvider.newMemoImageId()
        val extension = dataSource.detectExtension(source.value) ?: FALLBACK_EXTENSION
        val fileName = MemoImageFileName("${id.value}.$extension")
        dataSource.copyImage(source.value, fileName.value)
        return MemoImage(id = id, fileName = fileName)
    }

    override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
        fileNames.forEach { fileName ->
            dataSource.deleteImage(fileName.value)
        }
    }

    override fun resolveImagePath(fileName: MemoImageFileName): String =
        dataSource.imageFilePath(fileName.value)

    private companion object {
        const val FALLBACK_EXTENSION = "img"
    }

}
