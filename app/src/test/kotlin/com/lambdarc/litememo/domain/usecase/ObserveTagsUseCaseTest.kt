package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.tagFixture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveTagsUseCaseTest {

    @Test
    fun invokeReturnsTagsEmittedByRepository() = runTest {
        // Arrange
        val tag = tagFixture(id = "tag-1")
        val repository = FakeTagRepository(listOf(tag))

        // Act
        val tags = ObserveTagsUseCase(repository)().first()

        // Assert
        assertEquals(listOf(tag), tags)
    }

}
