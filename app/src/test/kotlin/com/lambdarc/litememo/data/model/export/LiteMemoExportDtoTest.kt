package com.lambdarc.litememo.data.model.export

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LiteMemoExportDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun deserializesValidJson() {
        // Arrange
        val jsonString = """
            {
                "version": 1,
                "exportedAt": 1716700000000,
                "tags": [
                    {
                        "id": "tag-1",
                        "name": "Work",
                        "colorArgb": 4285132580,
                        "createdAt": 1716600000000
                    }
                ],
                "memos": [
                    {
                        "id": "memo-1",
                        "title": "Title",
                        "body": "Body",
                        "createdAt": 1716600000000,
                        "updatedAt": 1716650000000,
                        "isFavorite": true,
                        "tagIds": ["tag-1"]
                    }
                ]
            }
        """.trimIndent()

        // Act
        val dto = json.decodeFromString<LiteMemoExportDto>(jsonString)

        // Assert
        assertEquals(
            LiteMemoExportDto(
                version = 1,
                exportedAt = 1716700000000L,
                tags = listOf(
                    TagExportDto(
                        id = "tag-1",
                        name = "Work",
                        colorArgb = 4285132580L,
                        createdAt = 1716600000000L
                    )
                ),
                memos = listOf(
                    MemoExportDto(
                        id = "memo-1",
                        title = "Title",
                        body = "Body",
                        createdAt = 1716600000000L,
                        updatedAt = 1716650000000L,
                        isFavorite = true,
                        tagIds = listOf("tag-1")
                    )
                )
            ),
            dto
        )
    }

    @Test
    fun ignoresUnknownFields() {
        // Arrange
        val jsonString = """
            {
                "version": 1,
                "exportedAt": 1000,
                "futureField": "ignored",
                "tags": [],
                "memos": []
            }
        """.trimIndent()

        // Act
        val dto = json.decodeFromString<LiteMemoExportDto>(jsonString)

        // Assert
        assertEquals(
            LiteMemoExportDto(
                version = 1,
                exportedAt = 1000L,
                tags = emptyList(),
                memos = emptyList()
            ),
            dto
        )
    }

    @Test
    fun throwsWhenRequiredFieldMissing() {
        // Arrange
        val jsonString = """
            {
                "version": 1,
                "tags": [],
                "memos": []
            }
        """.trimIndent()

        // Act & Assert
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LiteMemoExportDto>(jsonString)
        }
    }

    @Test
    fun defaultsTagIdsToEmptyList() {
        // Arrange
        val jsonString = """
            {
                "version": 1,
                "exportedAt": 1000,
                "tags": [],
                "memos": [
                    {
                        "id": "memo-1",
                        "title": "Title",
                        "body": "Body",
                        "createdAt": 1000,
                        "updatedAt": 1000,
                        "isFavorite": false
                    }
                ]
            }
        """.trimIndent()

        // Act
        val dto = json.decodeFromString<LiteMemoExportDto>(jsonString)

        // Assert
        assertEquals(emptyList<String>(), dto.memos[0].tagIds)
    }

    @Test
    fun serializesAndDeserializesRoundTrip() {
        // Arrange
        val original = LiteMemoExportDto(
            version = 1,
            exportedAt = 5000L,
            tags = listOf(
                TagExportDto(id = "tag-1", name = "Work", colorArgb = 0xFF6750A4, createdAt = 1000L)
            ),
            memos = listOf(
                MemoExportDto(
                    id = "memo-1",
                    title = "Title",
                    body = "Body",
                    createdAt = 2000L,
                    updatedAt = 3000L,
                    isFavorite = true,
                    tagIds = listOf("tag-1")
                )
            )
        )

        // Act
        val serialized = json.encodeToString(LiteMemoExportDto.serializer(), original)
        val deserialized = json.decodeFromString<LiteMemoExportDto>(serialized)

        // Assert
        assertEquals(original, deserialized)
    }

    @Test
    fun throwsForInvalidJson() {
        // Arrange
        val jsonString = "not a json"

        // Act & Assert
        assertThrows(SerializationException::class.java) {
            json.decodeFromString<LiteMemoExportDto>(jsonString)
        }
    }
}
