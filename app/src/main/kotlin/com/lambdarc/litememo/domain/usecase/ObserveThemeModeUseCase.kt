package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.domain.repository.DisplaySettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemeModeUseCase @Inject constructor(
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    operator fun invoke(): Flow<ThemeMode> = displaySettingsRepository.observeThemeMode()

}
