package com.lambdarc.litememo.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppLockSettingsRepository : AppLockSettingsRepository {

    private val appLockEnabled = MutableStateFlow(false)

    override fun observeAppLockEnabled(): Flow<Boolean> = appLockEnabled

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        appLockEnabled.value = enabled
    }
}
