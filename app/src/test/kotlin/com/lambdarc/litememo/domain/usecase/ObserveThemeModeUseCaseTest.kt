package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.ThemeMode
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveThemeModeUseCaseTest {

    @Test
    fun invokeReturnsDefaultThemeMode() = runTest {
        // Arrange
        val repository = FakeDisplaySettingsRepository()
        val useCase = ObserveThemeModeUseCase(repository)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(ThemeMode.SYSTEM, result)
    }

    @Test
    fun invokeReflectsUpdatedThemeMode() = runTest {
        // Arrange
        val repository = FakeDisplaySettingsRepository()
        val useCase = ObserveThemeModeUseCase(repository)
        repository.setThemeMode(ThemeMode.DARK)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(ThemeMode.DARK, result)
    }
}
