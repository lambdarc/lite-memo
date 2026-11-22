package com.lambdarc.litememo.data.mapper

import com.lambdarc.litememo.data.local.entity.TagEntity
import com.lambdarc.litememo.domain.tagFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class TagMapperTest {

    @Test
    fun toEntityReturnsTagEntityWithDomainValues() {
        // Arrange
        val tag = tagFixture(
            id = "tag-1",
            name = "Work",
            color = 0xFF6750A4,
            createdAt = 1000L
        )

        // Act
        val entity = tag.toEntity()

        // Assert
        assertEquals(
            TagEntity(
                id = "tag-1",
                name = "Work",
                colorArgb = 0xFF6750A4,
                createdAt = 1000L
            ),
            entity
        )
    }

    @Test
    fun toDomainReturnsTagWithEntityValues() {
        // Arrange
        val entity = entityFixture()

        // Act
        val tag = entity.toDomain()

        // Assert
        assertEquals(
            tagFixture(id = "tag-1", name = "Work", color = 0xFF6750A4, createdAt = 1000L),
            tag
        )
    }

    @TestFactory
    fun errorToDomainRejectsInvalidStoredValues() = mapOf(
        "blank id" to entityFixture().copy(id = " "),
        "blank name" to entityFixture().copy(name = " "),
        "negative color" to entityFixture().copy(colorArgb = -1L),
        "color exceeds 32 bits" to entityFixture().copy(colorArgb = 0x1FFFFFFFFL),
        "negative creation time" to entityFixture().copy(createdAt = -1L)
    ).map { (description, entity) ->
        dynamicTest(description) {
            // Act & Assert
            // Error: invalid storage values cannot bypass domain constraints during mapping.
            assertThrows(IllegalArgumentException::class.java) {
                entity.toDomain()
            }
        }
    }

    private fun entityFixture() = TagEntity(
        id = "tag-1",
        name = "Work",
        colorArgb = 0xFF6750A4,
        createdAt = 1000L
    )
}
