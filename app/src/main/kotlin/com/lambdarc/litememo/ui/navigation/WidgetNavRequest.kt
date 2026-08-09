package com.lambdarc.litememo.ui.navigation

import com.lambdarc.litememo.domain.model.value.MemoId

sealed class WidgetNavRequest {
    data object NewMemo : WidgetNavRequest()

    data class OpenMemo(val memoId: MemoId) : WidgetNavRequest()
}
