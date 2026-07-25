package com.appvoyager.litememo.data.local.dao

import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MemoDaoTest {

    @Test
    fun upsertMemoWithRefsThrowsBeforeWritingWhenTagRefsReferenceAnotherMemo() {
        // Arrange
        val dao = RecordingMemoBulkDao(failOnWrite = true)
        val memo = memoEntity(id = "memo-1")
        val tagRefs = listOf(MemoTagRefEntity(memoId = "memo-2", tagId = "tag-1", position = 0))

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest { dao.upsertMemoWithRefs(memo, tagRefs, emptyList()) }
        }
    }

    @Test
    fun upsertMemoWithRefsThrowsBeforeWritingWhenImageRefsReferenceAnotherMemo() {
        // Arrange
        val dao = RecordingMemoBulkDao(failOnWrite = true)
        val memo = memoEntity(id = "memo-1")
        val imageRefs = listOf(
            MemoImageEntity(
                id = "image-1",
                memoId = "memo-2",
                fileName = "image-1.jpg",
                position = 0
            )
        )

        // Act & Assert
        assertThrows(IllegalArgumentException::class.java) {
            runTest { dao.upsertMemoWithRefs(memo, emptyList(), imageRefs) }
        }
    }

    @Test
    fun upsertMemoWithRefsReplacesRefsAfterWritingMemo() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao()
        val memo = memoEntity(id = "memo-1")
        val tagRefs = listOf(
            MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0),
            MemoTagRefEntity(memoId = "memo-1", tagId = "tag-2", position = 1)
        )
        val imageRefs = listOf(
            MemoImageEntity(
                id = "image-1",
                memoId = "memo-1",
                fileName = "image-1.jpg",
                position = 0
            )
        )

        // Act
        dao.upsertMemoWithRefs(memo, tagRefs, imageRefs)

        // Assert
        assertEquals(
            listOf(
                "upsertMemo:memo-1",
                "deleteTagRefsForMemo:memo-1",
                "insertTagRefs:memo-1:tag-1:0,memo-1:tag-2:1",
                "deleteImageRefsForMemo:memo-1",
                "insertImageRefs:image-1:memo-1:image-1.jpg:0"
            ),
            dao.calls
        )
    }

    @Test
    fun upsertMemoWithRefsSkipsInsertWhenRefsAreEmpty() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao()
        val memo = memoEntity(id = "memo-1")

        // Act
        dao.upsertMemoWithRefs(memo, emptyList(), emptyList())

        // Assert
        assertEquals(
            listOf(
                "upsertMemo:memo-1",
                "deleteTagRefsForMemo:memo-1",
                "deleteImageRefsForMemo:memo-1"
            ),
            dao.calls
        )
    }

    @Test
    fun upsertAllMemosWithRefsCollectsImageFileNamesInBatches() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao()
        val memos = List(901) { index -> memoEntity(id = "memo-$index") }

        // Act
        dao.upsertAllMemosWithRefsAndCollectRemovedFileNames(
            memos = memos,
            tagRefsByMemoId = emptyMap(),
            imageRefsByMemoId = emptyMap()
        )

        // Assert
        assertEquals(listOf(900, 1), dao.imageFileNameBatchSizes)
    }

    private fun memoEntity(id: String) = MemoEntity(
        id = id,
        title = "Title",
        body = "Body",
        createdAt = 1000L,
        updatedAt = 1000L,
        isFavorite = false,
        deletedAt = null
    )

}
