package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel

sealed class HomeUiState {

    data object Loading : HomeUiState()

    data class Error(
        val selectedFilter: HomeFilterUiState = HomeFilterUiState.All,
        val search: SearchUiState = SearchUiState(),
        val selection: MemoSelectionUiState = MemoSelectionUiState()
    ) : HomeUiState()

    data class Content(
        val selectedFilter: HomeFilterUiState = HomeFilterUiState.All,
        val search: SearchUiState = SearchUiState(),
        val selection: MemoSelectionUiState = MemoSelectionUiState(),
        val allSelectedFavorite: Boolean = false,
        val allSelectedTagIds: Set<TagId> = emptySet(),
        val bulkTagDialog: HomeBulkTagDialogUiState = HomeBulkTagDialogUiState(),
        val tags: List<TagUiModel> = emptyList(),
        val memos: List<MemoUiModel> = emptyList()
    ) : HomeUiState()

}

sealed interface HomeFilterUiState {
    data object All : HomeFilterUiState
    data object Unorganized : HomeFilterUiState
    data object Favorite : HomeFilterUiState
    data class ByTag(val tagId: TagId) : HomeFilterUiState
}

data class HomeBulkTagDialogUiState(val isVisible: Boolean = false)
