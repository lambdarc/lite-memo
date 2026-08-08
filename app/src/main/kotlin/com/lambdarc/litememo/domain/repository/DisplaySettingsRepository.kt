package com.lambdarc.litememo.domain.repository

import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface DisplaySettingsRepository {

    fun observeThemeMode(): Flow<ThemeMode>

    fun observeMemoSortOrder(): Flow<MemoSortOrder>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setMemoSortOrder(order: MemoSortOrder)

}
