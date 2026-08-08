package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.usecase.SearchMemosUseCase
import com.lambdarc.litememo.ui.model.MemoUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class SearchUiState(
    val isActive: Boolean = false,
    val query: String = "",
    val hasError: Boolean = false,
    val results: List<MemoUiModel> = emptyList()
) {

    init {
        require(isActive || query.isEmpty()) {
            "SearchUiState query must be empty when search is inactive."
        }
        require(isActive || results.isEmpty()) {
            "SearchUiState results must be empty when search is inactive."
        }
    }

    fun opened(): SearchUiState = if (isActive) this else SearchUiState(isActive = true)

    fun closed(): SearchUiState = SearchUiState()

    fun toggled(): SearchUiState = if (isActive) closed() else opened()

    fun withQuery(query: String): SearchUiState = if (isActive) copy(query = query) else this

    fun withResult(
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

}

sealed interface MemoSearchUiResult {

    data object Inactive : MemoSearchUiResult

    data class Success(val query: String, val memos: List<Memo>) : MemoSearchUiResult

    data class Failure(val query: String) : MemoSearchUiResult
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
fun Flow<SearchUiState>.searchMemoResults(
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
                if (throwable is CancellationException) throw throwable
                emit(MemoSearchUiResult.Failure(query = search.query))
            }
    }
}

private const val SEARCH_DEBOUNCE_MILLIS = 250L
