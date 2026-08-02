package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.repository.FakeTutorialProgressRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompleteTutorialUseCaseTest {

    @Test
    fun normalPersistsCompletedValue() = runTest {
        // Arrange
        val repository = FakeTutorialProgressRepository()
        val useCase = CompleteTutorialUseCase(repository)

        // Act
        // Normal: completing tutorial persists the completion flag
        useCase()

        // Assert
        assertEquals(true, repository.observeTutorialCompleted().first())
    }
}
