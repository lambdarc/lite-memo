package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.ThemeMode
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    suspend operator fun invoke(mode: ThemeMode) = displaySettingsRepository.setThemeMode(mode)

}
