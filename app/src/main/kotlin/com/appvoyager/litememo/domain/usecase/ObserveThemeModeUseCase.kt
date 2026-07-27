package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.ThemeMode
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemeModeUseCase @Inject constructor(
    private val displaySettingsRepository: DisplaySettingsRepository
) {

    operator fun invoke(): Flow<ThemeMode> = displaySettingsRepository.observeThemeMode()

}
