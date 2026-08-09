package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SetThemeModeUseCaseTest {

    @Test
    fun invokePersistsThemeMode() = runTest {
        // Arrange
        val repository = FakeDisplaySettingsRepository()
        val useCase = SetThemeModeUseCase(repository)

        // Act
        useCase(ThemeMode.LIGHT)

        // Assert
        assertEquals(ThemeMode.LIGHT, repository.observeThemeMode().first())
    }
}
