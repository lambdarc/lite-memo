package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveMemoSortOrderUseCaseTest {

    @Test
    fun invokeReturnsDefaultSortOrder() = runTest {
        // Arrange
        val repository = FakeDisplaySettingsRepository()
        val useCase = ObserveMemoSortOrderUseCase(repository)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(MemoSortOrder.UPDATED_NEWEST, result)
    }

    @Test
    fun invokeReflectsUpdatedSortOrder() = runTest {
        // Arrange
        val repository = FakeDisplaySettingsRepository()
        val useCase = ObserveMemoSortOrderUseCase(repository)
        repository.setMemoSortOrder(MemoSortOrder.CREATED_NEWEST)

        // Act
        val result = useCase().first()

        // Assert
        assertEquals(MemoSortOrder.CREATED_NEWEST, result)
    }
}
