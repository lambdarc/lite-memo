package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoImageUiModel
import com.lambdarc.litememo.ui.model.TagUiModel

/**
 * メモ編集セッションの状態。
 *
 * [status] は画面本体の表示を切り替える。下書きと画像の所有情報は保存・復元・cleanup に
 * 使用するため、LOADING / ERROR に切り替わっても保持する。
 */
data class MemoEditUiState(
    val status: ScreenUiStatus = ScreenUiStatus.CONTENT,
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
