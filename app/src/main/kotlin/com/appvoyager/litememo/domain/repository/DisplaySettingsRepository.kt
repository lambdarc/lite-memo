package com.appvoyager.litememo.domain.repository

import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface DisplaySettingsRepository {

    fun observeThemeMode(): Flow<ThemeMode>

    fun observeMemoSortOrder(): Flow<MemoSortOrder>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setMemoSortOrder(order: MemoSortOrder)

}
