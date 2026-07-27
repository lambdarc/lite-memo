package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.repository.FakeDisplaySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SetMemoSortOrderUseCaseTest {

    @Test
    fun invokePersistsSortOrder() = runTest {
        // Arrange
        val repository = FakeDisplaySettingsRepository()
        val useCase = SetMemoSortOrderUseCase(repository)

        // Act
        useCase(MemoSortOrder.CREATED_NEWEST)

        // Assert
        assertEquals(MemoSortOrder.CREATED_NEWEST, repository.observeMemoSortOrder().first())
    }
}
