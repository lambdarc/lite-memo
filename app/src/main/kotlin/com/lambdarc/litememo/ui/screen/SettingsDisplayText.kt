package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.MemoSortOrder

@Composable
fun MemoSortOrder.toDisplayString(): String = when (this) {
    MemoSortOrder.UPDATED_NEWEST -> stringResource(R.string.settings_sort_updated)
    MemoSortOrder.CREATED_NEWEST -> stringResource(R.string.settings_sort_created)
}
