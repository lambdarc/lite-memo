package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.repository.MemoImageStore
import javax.inject.Inject

class AttachMemoImageUseCase @Inject constructor(private val memoImageStore: MemoImageStore) {

    suspend operator fun invoke(source: ImageSourceReference): MemoImage =
        memoImageStore.saveImage(source)

}
