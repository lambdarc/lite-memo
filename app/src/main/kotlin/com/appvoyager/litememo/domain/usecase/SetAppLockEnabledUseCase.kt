package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.repository.AppLockSettingsRepository
import javax.inject.Inject

class SetAppLockEnabledUseCase @Inject constructor(
    private val appLockSettingsRepository: AppLockSettingsRepository
) {

    suspend operator fun invoke(enabled: Boolean) =
        appLockSettingsRepository.setAppLockEnabled(enabled)

}
