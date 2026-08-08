package com.lambdarc.litememo.data.mapper

import com.lambdarc.litememo.data.local.entity.MemoEntity
import com.lambdarc.litememo.data.local.entity.MemoImageEntity
import com.lambdarc.litememo.data.local.entity.MemoTagRefEntity
import com.lambdarc.litememo.data.local.model.MemoSummaryProjection
import com.lambdarc.litememo.data.local.model.MemoWithRefs
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MemoMapperTest {

    @Test
    fun toEntityReturnsMemoEntityWithDomainValues() {
        // Arrange
        val memo = memoFixture(
            id = "memo-1",
            title = "Title",
            body = "Body",
            createdAt = 1000L,
            updatedAt = 2000L,
            isFavorite = true,
            deletedAt = 3000L
        )

        // Act
        val entity = memo.toEntity()

        // Assert
        assertEquals(
            MemoEntity(
                id = "memo-1",
                title = "Title",
                body = "Body",
                createdAt = 1000L,
                updatedAt = 2000L,
                isFavorite = true,
                deletedAt = 3000L
            ),
            entity
        )
    }

    @Test
    fun toTagRefsReturnsRefsOrderedByTagIds() {
        // Arrange
        val memo = memoFixture(
            id = "memo-1",
            tagIds = listOf(TagId("tag-1"), TagId("tag-2"))
        )

        // Act
        val tagRefs = memo.toTagRefs()

        // Assert
        assertEquals(
            listOf(
                MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0),
                MemoTagRefEntity(memoId = "memo-1", tagId = "tag-2", position = 1)
            ),
            tagRefs
        )
    }

    @Test
    fun toDomainReturnsTagIdsOrderedByPosition() {
        // Arrange
        val memoWithRefs = MemoWithRefs(
            memo = MemoEntity(
                id = "memo-1",
                title = "Title",
                body = "Body",
                createdAt = 1000L,
                updatedAt = 1000L,
                isFavorite = false,
                deletedAt = null
            ),
            tagRefs = listOf(
                MemoTagRefEntity(memoId = "memo-1", tagId = "tag-2", position = 1),
                MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0)
            ),
            imageRefs = emptyList()
        )

        // Act
        val memo = memoWithRefs.toDomain()

        // Assert
        assertEquals(listOf(TagId("tag-1"), TagId("tag-2")), memo.tagIds)
    }

    @Test
    fun normalToImageRefsAssignsPositionsInListOrder() {
        // Arrange
        val memo = memoFixture(
            id = "memo-1",
            images = listOf(
                memoImageFixture(id = "image-1", fileName = "image-1.jpg"),
                memoImageFixture(id = "image-2", fileName = "image-2.png")
            )
        )

        // Act
        // Normal: image order is persisted as positions.
        val imageRefs = memo.toImageRefs()

        // Assert
        assertEquals(
            listOf(
                MemoImageEntity(
                    id = "image-1",
                    memoId = "memo-1",
                    fileName = "image-1.jpg",
                    position = 0
                ),
                MemoImageEntity(
                    id = "image-2",
                    memoId = "memo-1",
                    fileName = "image-2.png",
                    position = 1
                )
            ),
            imageRefs
        )
    }

    @Test
    fun normalToDomainSortsImagesByPosition() {
        // Arrange
        val entity = MemoEntity(
            id = "memo-1",
            title = "Title",
            body = "Body",
            createdAt = 1000L,
            updatedAt = 2000L,
            isFavorite = false,
            deletedAt = null
        )
        val imageRefs = listOf(
            MemoImageEntity(
                id = "image-2",
                memoId = "memo-1",
                fileName = "image-2.png",
                position = 1
            ),
            MemoImageEntity(
                id = "image-1",
                memoId = "memo-1",
                fileName = "image-1.jpg",
                position = 0
            )
        )

        // Act
        // Normal: image refs round-trip in display order.
        val memo = entity.toDomain(tagRefs = emptyList(), imageRefs = imageRefs)

        // Assert
        assertEquals(
            listOf(
                memoImageFixture(id = "image-1", fileName = "image-1.jpg"),
                memoImageFixture(id = "image-2", fileName = "image-2.png")
            ),
            memo.images
        )
    }

    @Test
    fun toDomainReturnsMemoWithEntityValues() {
        // Arrange
        val entity = MemoEntity(
            id = "memo-1",
            title = "Title",
            body = "Body",
            createdAt = 1000L,
            updatedAt = 2000L,
            isFavorite = true,
            deletedAt = 3000L
        )

        // Act
        val memo = entity.toDomain(tagRefs = emptyList(), imageRefs = emptyList())

        // Assert
        assertEquals(
            Memo(
                id = MemoId("memo-1"),
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                createdAt = TimestampMillis(1000L),
                updatedAt = TimestampMillis(2000L),
                tagIds = emptyList(),
                images = emptyList(),
                isFavorite = true,
                deletedAt = TimestampMillis(3000L)
            ),
            memo
        )
    }

    @Test
    fun normalSummaryProjectionToDomainReturnsExplicitSummary() {
        // Arrange
        val projection = MemoSummaryProjection(
            id = "memo-1",
            title = "Title",
            body = "Body",
            isFavorite = true
        )

        // Act
        // Normal: memo-only query data maps to a summary instead of an incomplete Memo aggregate
        val summary = projection.toDomain()

        // Assert
        assertEquals(
            MemoSummary(
                id = MemoId("memo-1"),
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                isFavorite = true
            ),
            summary
        )
    }

    @Test
    fun toDomainThrowsWhenTagRefReferencesAnotherMemo() {
        // Arrange
        val entity = MemoEntity(
            id = "memo-1",
            title = "Title",
            body = "Body",
            createdAt = 1000L,
            updatedAt = 2000L,
            isFavorite = false,
            deletedAt = null
        )
        val tagRefs = listOf(
            MemoTagRefEntity(memoId = "memo-2", tagId = "tag-1", position = 0)
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            entity.toDomain(tagRefs = tagRefs, imageRefs = emptyList())
        }
    }

    @Test
    fun errorToDomainThrowsWhenImageRefsReferenceAnotherMemo() {
        // Arrange
        val entity = MemoEntity(
            id = "memo-1",
            title = "Title",
            body = "Body",
            createdAt = 1000L,
            updatedAt = 2000L,
            isFavorite = false,
            deletedAt = null
        )
        val imageRefs = listOf(
            MemoImageEntity(
                id = "image-1",
                memoId = "memo-2",
                fileName = "image-1.jpg",
                position = 0
            )
        )

        // Act & Assert
        // Error: image refs cannot be mapped across memo boundaries.
        assertThrows(IllegalArgumentException::class.java) {
            entity.toDomain(tagRefs = emptyList(), imageRefs = imageRefs)
        }
    }

    @Test
    fun toDomainThrowsWhenCreatedAtIsNegative() {
        // Arrange
        val entity = MemoEntity(
            id = "memo-1",
            title = "Title",
            body = "Body",
            createdAt = -1L,
            updatedAt = 2000L,
            isFavorite = false,
            deletedAt = null
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            entity.toDomain(tagRefs = emptyList(), imageRefs = emptyList())
        }
    }

}
