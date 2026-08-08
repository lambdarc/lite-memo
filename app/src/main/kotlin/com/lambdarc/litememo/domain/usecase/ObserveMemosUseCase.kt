package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.sortedBy
import com.lambdarc.litememo.domain.repository.DisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class ObserveMemosUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    operator fun invoke(): Flow<List<Memo>> = combine(
        memoRepository.observeActiveMemos(),
        displaySettingsRepository.observeMemoSortOrder()
    ) { memos, sortOrder ->
        memos.sortedBy(sortOrder)
    }

}
