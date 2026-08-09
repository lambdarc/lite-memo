package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.updatedAtFrom
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.repository.MemoRepository
import javax.inject.Inject

class MoveMemoToTrashUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val currentTimeProvider: CurrentTimeProvider
) {

    suspend operator fun invoke(id: MemoId): MemoId {
        val memo = requireNotNull(memoRepository.getActiveMemo(id)) {
            "Memo not found: ${id.value}"
        }
        val now = currentTimeProvider.now()
        val deletedAt = memo.updatedAtFrom(now)
        memoRepository.moveMemoToTrash(id, deletedAt)
        return id
    }

}
