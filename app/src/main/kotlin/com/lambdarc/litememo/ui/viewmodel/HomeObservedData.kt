package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.Tag

internal sealed interface HomeObservedData {

    data class Success(val memos: List<Memo>, val tags: List<Tag>) : HomeObservedData

    data object Failure : HomeObservedData
}
