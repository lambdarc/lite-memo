package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecentMemosUseCase @Inject constructor(private val memoRepository: MemoRepository) {

    operator fun invoke(limit: Int): Flow<List<MemoSummary>> =
        memoRepository.observeRecentActiveMemos(limit)

}
