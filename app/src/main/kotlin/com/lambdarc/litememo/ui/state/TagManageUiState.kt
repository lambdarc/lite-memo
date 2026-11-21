package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.theme.DEFAULT_TAG_COLORS

sealed class TagManageUiState {

    data object Loading : TagManageUiState()

    data object Error : TagManageUiState()

    data class Content(
        val tags: List<TagUiModel> = emptyList(),
        val editingTag: TagEditUiState? = null,
        val showDeleteDialog: TagUiModel? = null
    ) : TagManageUiState()

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
