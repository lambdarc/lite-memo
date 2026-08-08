package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.repository.AppLockSettingsRepository
import javax.inject.Inject

class SetAppLockEnabledUseCase @Inject constructor(
    private val appLockSettingsRepository: AppLockSettingsRepository
) {

    suspend operator fun invoke(enabled: Boolean) =
        appLockSettingsRepository.setAppLockEnabled(enabled)

}
