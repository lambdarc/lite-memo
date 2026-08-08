package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.repository.FakeAppLockSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SetAppLockEnabledUseCaseTest {

    @Test
    fun invokePersistsAppLockEnabled() = runTest {
        // Arrange
        val repository = FakeAppLockSettingsRepository()
        val useCase = SetAppLockEnabledUseCase(repository)

        // Act
        useCase(true)

        // Assert
        assertEquals(true, repository.observeAppLockEnabled().first())
    }
}
