package com.lambdarc.litememo.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow

class FakeAppLockSettingsRepository(deferInitialEmission: Boolean = false) :
    AppLockSettingsRepository {

    private val appLockEnabled = MutableStateFlow(if (deferInitialEmission) null else false)
    var observeError: Throwable? = null

    override fun observeAppLockEnabled(): Flow<Boolean> =
        observeError?.let { error -> flow { throw error } } ?: appLockEnabled.filterNotNull()

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        appLockEnabled.value = enabled
    }
}
