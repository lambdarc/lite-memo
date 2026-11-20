package com.lambdarc.litememo.data.mapper

import com.lambdarc.litememo.data.export.imageDtoFixture
import com.lambdarc.litememo.data.model.export.LiteMemoExportDto
import com.lambdarc.litememo.data.model.export.MemoExportDto
import com.lambdarc.litememo.data.model.export.MemoImageExportDto
import com.lambdarc.litememo.data.model.export.TagExportDto
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.nonBlankText
import com.lambdarc.litememo.text
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

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
            assertEquals(
                ExportData(
                    version = 1,
                    exportedAt = TimestampMillis(5000L),
                    tags = listOf(
                        tagFixture(
                            id = "tag-1",
                            name = "Work",
                            color = 0xFF6750A4,
                            createdAt = 1000L
                        )
                    ),
                    memos = listOf(
                        memoFixture(
                            id = "memo-1",
                            title = "Title",
                            body = "Body",
                            createdAt = 2000L,
                            updatedAt = 3000L,
                            isFavorite = true,
                            tagIds = listOf(TagId("tag-1"))
                        )
                    )
                ),
                domain
            )
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
            assertEquals(original, roundTripped)
        }

        @Test
        fun propertyMemoWithImagesSurvivesExportAndImport() = runTest {
            // Act & Assert
            // Property: any active memo, images and odd text included, maps back unchanged
            checkAll(activeMemos) { original ->
                val imageDtos = original.images.mapIndexed { index, image ->
                    imageDtoFixture(
                        content = ByteArray(0),
                        index = index + 1,
                        id = image.id.value,
                        fileName = image.fileName.value
                    )
                }
                val storedFileNames = original.images.associate { it.id.value to it.fileName }

                val roundTripped = original.toExportDto(imageDtos).toDomain(storedFileNames)

                assertEquals(original, roundTripped)
            }
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

        @Test
        fun propertyTagSurvivesExportAndImport() = runTest {
            // Act & Assert
            // Property: any tag, full ARGB range and odd names included, maps back unchanged
            checkAll(tags) { original ->
                assertEquals(original, original.toExportDto().toDomain())
            }
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

    private companion object {
        val LATEST_TIMESTAMP_MILLIS = Instant.parse("2200-01-01T00:00:00Z").toEpochMilli()

        val activeMemos: Arb<Memo> = arbitrary {
            val createdAt = Arb.long(0L..LATEST_TIMESTAMP_MILLIS).bind()
            val images = Arb.list(Arb.text(0..12), 0..3).bind().mapIndexed { index, name ->
                memoImageFixture(
                    id = "image-${index + 1}",
                    fileName = "picked-${index + 1}" +
                        name.filterNot { it.isISOControl() || it == '/' || it == '\\' }
                )
            }
            memoFixture(
                id = Arb.nonBlankText().bind(),
                title = Arb.text().bind(),
                body = Arb.text(0..200).bind(),
                createdAt = createdAt,
                updatedAt = createdAt + Arb.long(0L..LATEST_TIMESTAMP_MILLIS - createdAt).bind(),
                tagIds = Arb.list(Arb.nonBlankText(1..12), 0..3).bind().map { TagId(it) },
                images = images,
                isFavorite = Arb.boolean().bind()
            )
        }

        val tags: Arb<Tag> = arbitrary {
            tagFixture(
                id = Arb.nonBlankText().bind(),
                name = Arb.nonBlankText().bind(),
                color = Arb.long(0L..0xFFFFFFFFL).bind(),
                createdAt = Arb.long(0L..LATEST_TIMESTAMP_MILLIS).bind()
            )
        }
    }
}
