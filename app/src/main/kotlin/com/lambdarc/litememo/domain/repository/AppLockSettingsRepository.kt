package com.lambdarc.litememo.domain.repository

import kotlinx.coroutines.flow.Flow

interface AppLockSettingsRepository {

    fun observeAppLockEnabled(): Flow<Boolean>

    suspend fun setAppLockEnabled(enabled: Boolean)

}
