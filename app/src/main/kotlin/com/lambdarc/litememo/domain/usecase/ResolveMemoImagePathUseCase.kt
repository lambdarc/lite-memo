package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.repository.MemoImageStore
import javax.inject.Inject

class ResolveMemoImagePathUseCase @Inject constructor(private val memoImageStore: MemoImageStore) {

    operator fun invoke(fileName: MemoImageFileName): String =
        memoImageStore.resolveImagePath(fileName)

}
