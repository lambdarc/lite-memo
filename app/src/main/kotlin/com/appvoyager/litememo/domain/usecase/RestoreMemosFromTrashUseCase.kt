package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.repository.MemoRepository
import javax.inject.Inject

class RestoreMemosFromTrashUseCase @Inject constructor(private val memoRepository: MemoRepository) {

    suspend operator fun invoke(ids: List<MemoId>) {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return
        memoRepository.restoreMemosFromTrash(distinctIds)
    }

}
