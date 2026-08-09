package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.repository.MemoRepository
import javax.inject.Inject

class RestoreMemoFromTrashUseCase @Inject constructor(private val memoRepository: MemoRepository) {

    suspend operator fun invoke(id: MemoId) = memoRepository.restoreMemoFromTrash(id)

}
