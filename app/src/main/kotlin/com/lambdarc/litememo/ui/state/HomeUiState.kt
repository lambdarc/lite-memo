package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel

data class HomeUiState(
    val status: ScreenUiStatus = ScreenUiStatus.LOADING,
    val selectedFilter: HomeFilterUiState = HomeFilterUiState.All,
    val search: SearchUiState = SearchUiState(),
    val selection: MemoSelectionUiState = MemoSelectionUiState(),
    val allSelectedFavorite: Boolean = false,
    val allSelectedTagIds: Set<TagId> = emptySet(),
    val bulkTagDialog: HomeBulkTagDialogUiState = HomeBulkTagDialogUiState(),
    val tags: List<TagUiModel> = emptyList(),
    val memos: List<MemoUiModel> = emptyList()
) {

    init {
        val isContent = status == ScreenUiStatus.CONTENT
        require(isContent || memos.isEmpty()) {
            "HomeUiState memos must be empty unless status is CONTENT."
        }
        require(isContent || tags.isEmpty()) {
            "HomeUiState tags must be empty unless status is CONTENT."
        }
        require(isContent || !bulkTagDialog.isVisible) {
            "HomeUiState bulkTagDialog must be hidden unless status is CONTENT."
        }
        require(isContent || allSelectedTagIds.isEmpty()) {
            "HomeUiState allSelectedTagIds must be empty unless status is CONTENT."
        }
        require(isContent || !allSelectedFavorite) {
            "HomeUiState allSelectedFavorite must be false unless status is CONTENT."
        }
    }

}

sealed interface HomeFilterUiState {
    data object All : HomeFilterUiState
    data object Unorganized : HomeFilterUiState
    data object Favorite : HomeFilterUiState
    data class ByTag(val tagId: TagId) : HomeFilterUiState
}

data class HomeBulkTagDialogUiState(val isVisible: Boolean = false)
