package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.provider.MemoIdProvider
import javax.inject.Inject

class GenerateMemoIdUseCase @Inject constructor(private val memoIdProvider: MemoIdProvider) {

    operator fun invoke(): MemoId = memoIdProvider.newMemoId()

}
