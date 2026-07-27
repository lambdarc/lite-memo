package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.repository.AppLockSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAppLockEnabledUseCase @Inject constructor(
    private val appLockSettingsRepository: AppLockSettingsRepository
) {

    operator fun invoke(): Flow<Boolean> = appLockSettingsRepository.observeAppLockEnabled()

}
