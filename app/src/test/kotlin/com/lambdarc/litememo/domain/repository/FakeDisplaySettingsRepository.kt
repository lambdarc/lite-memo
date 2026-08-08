package com.lambdarc.litememo.domain.repository

import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeDisplaySettingsRepository : DisplaySettingsRepository {

    private val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val sortOrder = MutableStateFlow(MemoSortOrder.UPDATED_NEWEST)

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode

    override fun observeMemoSortOrder(): Flow<MemoSortOrder> = sortOrder

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }

    override suspend fun setMemoSortOrder(order: MemoSortOrder) {
        sortOrder.value = order
    }
}
