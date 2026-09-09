package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoImageUiModel
import com.lambdarc.litememo.ui.model.TagUiModel

data class MemoEditUiState(
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val hasTagError: Boolean = false,
    val memoId: String? = null,
    val title: String = "",
    val body: String = "",
    val isFavorite: Boolean = false,
    val isModified: Boolean = false,
    val isDeletePending: Boolean = false,
    val availableTags: List<TagUiModel> = emptyList(),
    val selectedTagIds: Set<TagId> = emptySet(),
    val images: List<MemoImageUiModel> = emptyList()
)
