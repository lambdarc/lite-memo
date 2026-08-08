package com.lambdarc.litememo.data.mapper

import com.lambdarc.litememo.data.local.entity.TagEntity
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.TagColor
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TagName
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.tagFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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
        val entity = TagEntity(
            id = "tag-1",
            name = "Work",
            colorArgb = 0xFF6750A4,
            createdAt = 1000L
        )

        // Act
        val tag = entity.toDomain()

        // Assert
        assertEquals(
            Tag(
                id = TagId("tag-1"),
                name = TagName("Work"),
                color = TagColor(0xFF6750A4),
                createdAt = TimestampMillis(1000L)
            ),
            tag
        )
    }

    @Test
    fun toDomainThrowsWhenNameIsBlank() {
        // Arrange
        val entity = TagEntity(
            id = "tag-1",
            name = " ",
            colorArgb = 0xFF6750A4,
            createdAt = 1000L
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            entity.toDomain()
        }
    }

}
