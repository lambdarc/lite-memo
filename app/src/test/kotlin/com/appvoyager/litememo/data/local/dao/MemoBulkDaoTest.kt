package com.appvoyager.litememo.data.local.dao

import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.model.MemoWithRefs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemoBulkDaoTest {

    @Test
    fun boundaryGetActiveMemosWithRefsPreservesDistinctInputOrder() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao(
            activeMemosById = mapOf(
                "memo-1" to memoWithRefs(id = "memo-1"),
                "memo-2" to memoWithRefs(id = "memo-2")
            ),
            reverseActiveMemoQueryResults = true
        )

        // Act
        // Boundary: query row order must not replace the caller's distinct ID order
        val memos = dao.getActiveMemosWithRefs(listOf("memo-2", "memo-1", "memo-2"))

        // Assert
        assertEquals(listOf("memo-2", "memo-1"), memos.map { it.memo.id })
    }

    @Test
    fun boundaryMoveMemosToTrashSkipsReadsAndWritesForEmptyInput() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao(failOnWrite = true)

        // Act
        // Boundary: an empty bulk operation reads nothing and writes nothing
        dao.moveMemosToTrash(emptyMap())

        // Assert
        assertEquals(
            emptyList<String>() to emptyList<List<String>>(),
            dao.calls to dao.activeMemoIdReads
        )
    }

    @Test
    fun boundaryRestoreMemosFromTrashUsesSqliteSafeBatches() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao()
        val ids = List(901) { index -> "memo-$index" }

        // Act
        // Boundary: validation and update queries stay below SQLite's bind limit
        dao.restoreMemosFromTrash(ids)

        // Assert
        assertEquals(
            listOf(900, 1) to listOf(900, 1),
            dao.trashedMemoIdBatchSizes to dao.restoredMemoBatchSizes
        )
    }

    @Test
    fun boundaryMoveMemosToTrashSkipsMemosNoLongerActive() = runTest {
        // Arrange
        val dao = RecordingMemoBulkDao(activeMemoIds = setOf("memo-1"))

        // Act
        // Boundary: a concurrently trashed member is skipped while active members are trashed
        dao.moveMemosToTrash(
            linkedMapOf(
                "memo-1" to 1_000L,
                "memo-2" to 2_000L
            )
        )

        // Assert
        assertEquals(
            listOf("moveMemosToTrashBatch:memo-1:1000"),
            dao.calls
        )
    }

    private fun memoWithRefs(id: String) = MemoWithRefs(
        memo = MemoEntity(
            id = id,
            title = "Title",
            body = "Body",
            createdAt = 1000L,
            updatedAt = 1000L,
            isFavorite = false,
            deletedAt = null
        ),
        tagRefs = emptyList(),
        imageRefs = emptyList()
    )

}
