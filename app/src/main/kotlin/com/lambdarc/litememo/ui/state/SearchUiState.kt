package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.ui.model.MemoUiModel

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

    fun updateQuery(query: String): SearchUiState = if (isActive) copy(query = query) else this

}
