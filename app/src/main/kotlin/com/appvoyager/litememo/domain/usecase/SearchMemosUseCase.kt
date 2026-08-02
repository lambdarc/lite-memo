package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.sortedBy
import com.appvoyager.litememo.domain.model.value.SearchQuery
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class SearchMemosUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    operator fun invoke(query: String): Flow<List<Memo>> {
        val searchQuery = SearchQuery.fromOrNull(query) ?: return flowOf(emptyList())

        return combine(
            memoRepository.observeActiveMemosBySearchQuery(searchQuery),
            displaySettingsRepository.observeMemoSortOrder()
        ) { memos, sortOrder ->
            memos.sortedBy(sortOrder)
        }
    }

}
