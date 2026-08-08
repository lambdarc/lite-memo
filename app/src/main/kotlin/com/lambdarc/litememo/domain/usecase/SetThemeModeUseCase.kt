package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.domain.repository.DisplaySettingsRepository
import javax.inject.Inject

class SetThemeModeUseCase @Inject constructor(
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    suspend operator fun invoke(mode: ThemeMode) = displaySettingsRepository.setThemeMode(mode)

}
