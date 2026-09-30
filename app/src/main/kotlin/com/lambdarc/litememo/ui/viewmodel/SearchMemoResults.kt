package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.usecase.SearchMemosUseCase
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.state.SearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun Flow<SearchUiState>.searchMemoResults(
    searchMemosUseCase: SearchMemosUseCase
): Flow<MemoSearchUiResult> = debounce { search ->
    if (!search.isActive || search.query.isBlank()) 0L else SEARCH_DEBOUNCE_MILLIS
}.flatMapLatest { search ->
    when {
        !search.isActive -> flowOf(MemoSearchUiResult.Inactive)

        search.query.isBlank() -> flowOf(
            MemoSearchUiResult.Success(query = search.query, memos = emptyList())
        )

        else -> searchMemosUseCase(search.query)
            .map<List<Memo>, MemoSearchUiResult> { memos ->
                MemoSearchUiResult.Success(query = search.query, memos = memos)
            }
            .catch { throwable ->
                if (throwable is CancellationException) currentCoroutineContext().ensureActive()
                emit(MemoSearchUiResult.Failure(query = search.query))
            }
    }
}

internal fun SearchUiState.applySearchResult(
    result: MemoSearchUiResult,
    mapMemos: (List<Memo>) -> List<MemoUiModel>
): SearchUiState {
    if (!isActive) return SearchUiState()

    return when (result) {
        MemoSearchUiResult.Inactive -> copy(hasError = false, results = emptyList())

        is MemoSearchUiResult.Success -> copy(
            hasError = false,
            results = if (result.query == query) mapMemos(result.memos) else emptyList()
        )

        is MemoSearchUiResult.Failure -> copy(
            hasError = result.query == query,
            results = emptyList()
        )
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 250L
