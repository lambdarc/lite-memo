package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.tagFixture
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeleteTagUseCaseTest {

    @Test
    fun invokeDeletesTagById() = runTest {
        // Arrange
        val tag = tagFixture(id = "tag-1")
        val tagRepository = FakeTagRepository(listOf(tag))

        // Act
        DeleteTagUseCase(tagRepository)(tag.id)

        // Assert
        assertEquals(listOf(tag.id), tagRepository.deletedIds)
    }

}
