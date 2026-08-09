package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.repository.MemoRepository
import javax.inject.Inject

class GetMemoUseCase @Inject constructor(private val memoRepository: MemoRepository) {

    suspend operator fun invoke(id: MemoId): Memo? = memoRepository.getActiveMemo(id)

}
