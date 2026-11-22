package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.model.Memo

internal sealed interface MemoSearchUiResult {

    data object Inactive : MemoSearchUiResult

    data class Success(val query: String, val memos: List<Memo>) : MemoSearchUiResult

    data class Failure(val query: String) : MemoSearchUiResult
}
