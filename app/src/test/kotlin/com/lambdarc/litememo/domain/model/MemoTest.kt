package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MemoTest {

    @Test
    fun constructorThrowsWhenUpdatedAtIsBeforeCreatedAt() {
        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            Memo(
                id = MemoId("memo-1"),
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                createdAt = TimestampMillis(2000L),
                updatedAt = TimestampMillis(1000L)
            )
        }
    }

    @Test
    fun errorInitThrowsWhenImagesContainDuplicatedIds() {
        // Arrange
        val imageId = MemoImageId("image-1")
        val images = listOf(
            MemoImage(id = imageId, fileName = MemoImageFileName("image-1.jpg")),
            MemoImage(id = imageId, fileName = MemoImageFileName("image-2.jpg"))
        )

        // Act & Assert
        // Error: one memo cannot reference duplicated image ids.
        assertThrows(IllegalArgumentException::class.java) {
            Memo(
                id = MemoId("memo-1"),
                title = MemoTitle("Title"),
                body = MemoBody("Body"),
                createdAt = TimestampMillis(1000L),
                updatedAt = TimestampMillis(1000L),
                images = images
            )
        }
    }

    @Test
    fun boundaryConstructorAcceptsUpdatedAtEqualToCreatedAt() {
        // Act & Assert
        // Boundary: an unedited memo keeps updatedAt equal to createdAt
        assertDoesNotThrow { memoFixture(createdAt = 1000L, updatedAt = 1000L) }
    }

    @Test
    fun errorConstructorThrowsWhenDeletedAtIsBeforeUpdatedAt() {
        // Act & Assert
        // Error: a memo cannot be trashed one millisecond before its last update
        assertThrows(IllegalArgumentException::class.java) {
            memoFixture(createdAt = 1000L, updatedAt = 2000L, deletedAt = 1999L)
        }
    }

    @Test
    fun boundaryConstructorAcceptsDeletedAtEqualToUpdatedAt() {
        // Act & Assert
        // Boundary: trashing in the same millisecond as the last update is allowed
        assertDoesNotThrow {
            memoFixture(createdAt = 1000L, updatedAt = 2000L, deletedAt = 2000L)
        }
    }

    @Test
    fun normalUpdatedAtFromReturnsNowWhenNowIsLatest() {
        // Arrange
        val memo = memoFixture(createdAt = 1000L, updatedAt = 2000L)

        // Act
        // Normal: the current time becomes the next updatedAt
        val updatedAt = memo.updatedAtFrom(TimestampMillis(3000L))

        // Assert
        assertEquals(TimestampMillis(3000L), updatedAt)
    }

    @Test
    fun boundaryUpdatedAtFromKeepsUpdatedAtWhenClockGoesBackwards() {
        // Arrange
        val memo = memoFixture(createdAt = 1000L, updatedAt = 2000L)

        // Act
        // Boundary: a clock earlier than the stored updatedAt never moves it backwards
        val updatedAt = memo.updatedAtFrom(TimestampMillis(1999L))

        // Assert
        assertEquals(TimestampMillis(2000L), updatedAt)
    }

}
