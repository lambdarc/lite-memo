package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.repository.FakeAppLockSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveAppLockEnabledUseCaseTest {

    @Test
    fun invokeReturnsDefaultAppLockEnabled() = runTest {
        // Arrange
        val repository = FakeAppLockSettingsRepository()
        val useCase = ObserveAppLockEnabledUseCase(repository)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun invokeReflectsUpdatedAppLockEnabled() = runTest {
        // Arrange
        val repository = FakeAppLockSettingsRepository()
        val useCase = ObserveAppLockEnabledUseCase(repository)
        repository.setAppLockEnabled(true)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(true, result)
    }
}
