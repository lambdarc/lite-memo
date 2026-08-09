package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.repository.MemoImageStore
import javax.inject.Inject

class DeleteMemoImagesUseCase @Inject constructor(private val memoImageStore: MemoImageStore) {

    suspend operator fun invoke(fileNames: List<MemoImageFileName>) =
        memoImageStore.deleteImages(fileNames)

}
