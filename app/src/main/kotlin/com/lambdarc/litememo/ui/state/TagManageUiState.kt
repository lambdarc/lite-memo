package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.theme.DEFAULT_TAG_COLORS

data class TagManageUiState(
    val status: ScreenUiStatus = ScreenUiStatus.LOADING,
    val tags: List<TagUiModel> = emptyList(),
    val editingTag: TagEditUiState? = null,
    val showDeleteDialog: TagUiModel? = null
) {

    init {
        val isContent = status == ScreenUiStatus.CONTENT
        require(isContent || tags.isEmpty()) {
            "TagManageUiState tags must be empty unless status is CONTENT."
        }
        require(isContent || editingTag == null) {
            "TagManageUiState editingTag must be null unless status is CONTENT."
        }
        require(isContent || showDeleteDialog == null) {
            "TagManageUiState showDeleteDialog must be null unless status is CONTENT."
        }
    }

}

data class TagEditUiState(
    val id: TagId? = null,
    val name: String = "",
    val colorArgb: Long = DEFAULT_TAG_COLORS.first().argb,
    val nameError: Boolean = false,
    val duplicateNameError: Boolean = false,
    val saveError: Boolean = false,
    val isSaving: Boolean = false
)
