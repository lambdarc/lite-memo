package com.appvoyager.litememo.ui.state

import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.ui.model.MemoUiModel
import com.appvoyager.litememo.ui.model.TagUiModel

data class HomeUiState(
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
    val selectedFilter: HomeFilterUiState = HomeFilterUiState.All,
    val search: SearchUiState = SearchUiState(),
    val selection: MemoSelectionUiState = MemoSelectionUiState(),
    val allSelectedFavorite: Boolean = false,
    val allSelectedTagIds: Set<TagId> = emptySet(),
    val bulkTagDialog: HomeBulkTagDialogUiState = HomeBulkTagDialogUiState(),
    val tags: List<TagUiModel> = emptyList(),
    val memos: List<MemoUiModel> = emptyList()
)

sealed interface HomeFilterUiState {
    data object All : HomeFilterUiState
    data object Unorganized : HomeFilterUiState
    data object Favorite : HomeFilterUiState
    data class ByTag(val tagId: TagId) : HomeFilterUiState
}

data class HomeBulkTagDialogUiState(val isVisible: Boolean = false)
