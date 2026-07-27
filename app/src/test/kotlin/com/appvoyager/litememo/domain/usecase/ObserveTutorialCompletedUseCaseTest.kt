package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.repository.FakeTutorialProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveTutorialCompletedUseCaseTest {

    @Test
    fun normalReturnsDefaultValue() = runTest {
        // Arrange
        val repository = FakeTutorialProgressRepository()
        val useCase = ObserveTutorialCompletedUseCase(repository)

        // Act
        // Normal: default tutorial completion is false
        val result = useCase().first()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun normalReturnsCompletedValue() = runTest {
        // Arrange
        val repository = FakeTutorialProgressRepository()
        val useCase = ObserveTutorialCompletedUseCase(repository)
        repository.completeTutorial()

        // Act
        // Normal: saved tutorial completion is observed
        val result = useCase().first()

        // Assert
        assertEquals(true, result)
    }
}
