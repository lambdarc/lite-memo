package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TimestampMillis
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

}
