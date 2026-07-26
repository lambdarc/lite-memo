package com.appvoyager.litememo.data.mapper

import com.appvoyager.litememo.data.model.export.LiteMemoExportDto
import com.appvoyager.litememo.data.model.export.MemoExportDto
import com.appvoyager.litememo.data.model.export.MemoImageExportDto
import com.appvoyager.litememo.data.model.export.TagExportDto
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.MemoImage
import com.appvoyager.litememo.domain.model.value.MemoBody
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.MemoImageFileName
import com.appvoyager.litememo.domain.model.value.MemoImageId
import com.appvoyager.litememo.domain.model.value.MemoTitle
import com.appvoyager.litememo.domain.model.value.TagColor
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.tagFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ExportDataMapperTest {

    @Nested
    inner class DtoToDomain {

        @Test
        fun convertsDtoToDomain() {
            // Arrange
            val dto = LiteMemoExportDto(
                version = 1,
                exportedAt = 5000L,
                tags = listOf(
                    TagExportDto(
                        id = "tag-1",
                        name = "Work",
                        colorArgb = 0xFF6750A4,
                        createdAt = 1000L
                    )
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
            val domain = dto.toDomain()

            // Assert
            assertEquals(1, domain.version)
            assertEquals(TimestampMillis(5000L), domain.exportedAt)
            assertEquals(TagId("tag-1"), domain.tags[0].id)
            assertEquals(TagName("Work"), domain.tags[0].name)
            assertEquals(TagColor(0xFF6750A4), domain.tags[0].color)
            assertEquals(MemoId("memo-1"), domain.memos[0].id)
            assertEquals(MemoTitle("Title"), domain.memos[0].title)
            assertEquals(MemoBody("Body"), domain.memos[0].body)
            assertEquals(true, domain.memos[0].isFavorite)
            assertEquals(listOf(TagId("tag-1")), domain.memos[0].tagIds)
        }

        @Test
        fun importedMemoHasNullDeletedAt() {
            // Arrange
            val dto = MemoExportDto(
                id = "memo-1",
                title = "Title",
                body = "Body",
                createdAt = 1000L,
                updatedAt = 1000L,
                isFavorite = false,
                tagIds = emptyList()
            )

            // Act
            val memo = dto.toDomain()

            // Assert
            assertNull(memo.deletedAt)
        }
    }

    @Nested
    inner class MemoRoundTrip {

        @Test
        fun memoSurvivesRoundTrip() {
            // Arrange
            val original = memoFixture(
                id = "memo-1",
                title = "Title",
                body = "Body\nLine2",
                createdAt = 2000L,
                updatedAt = 3000L,
                isFavorite = true,
                tagIds = listOf(TagId("tag-1"), TagId("tag-2"))
            )

            // Act
            val roundTripped = original.toExportDto(emptyList()).toDomain()

            // Assert
            assertEquals(original.id, roundTripped.id)
            assertEquals(original.title, roundTripped.title)
            assertEquals(original.body, roundTripped.body)
            assertEquals(original.createdAt, roundTripped.createdAt)
            assertEquals(original.updatedAt, roundTripped.updatedAt)
            assertEquals(original.isFavorite, roundTripped.isFavorite)
            assertEquals(original.tagIds, roundTripped.tagIds)
            assertNull(roundTripped.deletedAt)
        }
    }

    @Nested
    inner class TagRoundTrip {

        @Test
        fun tagSurvivesRoundTrip() {
            // Arrange
            val original = tagFixture(
                id = "tag-1",
                name = "Work",
                color = 0xFF6750A4,
                createdAt = 1000L
            )

            // Act
            val roundTripped = original.toExportDto().toDomain()

            // Assert
            assertEquals(original, roundTripped)
        }
    }

    @Nested
    inner class ValidationOnConversion {

        @Test
        fun throwsWhenMemoIdIsBlank() {
            // Arrange
            val dto = MemoExportDto(
                id = " ",
                title = "Title",
                body = "Body",
                createdAt = 1000L,
                updatedAt = 1000L,
                isFavorite = false,
                tagIds = emptyList()
            )

            // Act & Assert
            assertThrows(IllegalArgumentException::class.java) {
                dto.toDomain()
            }
        }

        @Test
        fun throwsWhenTagNameIsEmpty() {
            // Arrange
            val dto = TagExportDto(
                id = "tag-1",
                name = "",
                colorArgb = 0xFF6750A4,
                createdAt = 1000L
            )

            // Act & Assert
            assertThrows(IllegalArgumentException::class.java) {
                dto.toDomain()
            }
        }

        @Test
        fun throwsWhenTimestampIsNegative() {
            // Arrange
            val dto = MemoExportDto(
                id = "memo-1",
                title = "Title",
                body = "Body",
                createdAt = -1L,
                updatedAt = 1000L,
                isFavorite = false,
                tagIds = emptyList()
            )

            // Act & Assert
            assertThrows(IllegalArgumentException::class.java) {
                dto.toDomain()
            }
        }
    }

    @Nested
    inner class ImportDtoToDomain {

        @Test
        fun normalToDomainKeepsImageIdsAndOrderWithStoredFileNames() {
            // Arrange
            val dto = manifestDto(
                images = listOf(imageDto(id = "image-1"), imageDto(id = "image-2"))
            )
            val storedFileNames = mapOf(
                "image-1" to MemoImageFileName("session-00000001.jpg"),
                "image-2" to MemoImageFileName("session-00000002.png")
            )

            // Act
            // Normal: image ids and order survive while file names are replaced by stored ones.
            val data = dto.toDomain(storedFileNames)

            // Assert
            assertEquals(
                listOf(
                    MemoImage(MemoImageId("image-1"), MemoImageFileName("session-00000001.jpg")),
                    MemoImage(MemoImageId("image-2"), MemoImageFileName("session-00000002.png"))
                ),
                data.memos.single().images
            )
        }

        @Test
        fun boundaryToDomainAcceptsImageOnlyMemo() {
            // Arrange
            val dto = manifestDto(title = "", body = "", images = listOf(imageDto()))
            val storedFileNames = mapOf("image-1" to MemoImageFileName("session-00000001.jpg"))

            // Act
            // Boundary: a memo with empty title and body keeps its images.
            val data = dto.toDomain(storedFileNames)

            // Assert
            assertEquals(1, data.memos.single().images.size)
        }

        @Test
        fun errorToDomainRejectsImageWithoutStoredFileName() {
            // Arrange
            val dto = manifestDto(images = listOf(imageDto()))

            // Act & Assert
            // Error: an image the store did not accept must not reach the domain model.
            assertThrows(IllegalArgumentException::class.java) {
                dto.toDomain(emptyMap())
            }
        }

        private fun manifestDto(
            title: String = "Title",
            body: String = "Body",
            images: List<MemoImageExportDto> = emptyList()
        ) = LiteMemoExportDto(
            version = 1,
            exportedAt = 5000L,
            tags = emptyList(),
            memos = listOf(
                MemoExportDto(
                    id = "memo-1",
                    title = title,
                    body = body,
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    isFavorite = false,
                    images = images
                )
            )
        )

        private fun imageDto(id: String = "image-1") = MemoImageExportDto(
            id = id,
            fileName = "picked.jpg",
            archiveEntry = "images/00000001",
            sizeBytes = 16L,
            sha256 = "0".repeat(64)
        )
    }
}
