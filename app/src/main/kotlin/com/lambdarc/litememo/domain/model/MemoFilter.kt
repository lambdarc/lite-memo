package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TagId

sealed interface MemoFilter {
    data object All : MemoFilter
    data object Unorganized : MemoFilter
    data object Favorite : MemoFilter
    data class ByTag(val tagId: TagId) : MemoFilter
}
