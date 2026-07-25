package com.appvoyager.litememo.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appvoyager.litememo.data.local.LiteMemoDatabase
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import com.appvoyager.litememo.data.local.entity.TagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDaoInstrumentedTest {

    private lateinit var database: LiteMemoDatabase
    private lateinit var memoDao: MemoDao
    private lateinit var memoBulkDao: MemoBulkDao
    private lateinit var tagDao: TagDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LiteMemoDatabase::class.java).build()
        memoDao = database.memoDao()
        memoBulkDao = database.memoBulkDao()
        tagDao = database.tagDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeTagsReturnsTagsOrderedByCreatedAtThenId() = runTest {
        // Arrange
        tagDao.insertOrUpdateAllTags(
            listOf(tagEntity(id = "tag-c", name = "Tag C", createdAt = 2_000L))
        )
        tagDao.insertOrUpdateAllTags(
            listOf(tagEntity(id = "tag-b", name = "Tag B", createdAt = 1_000L))
        )
        tagDao.insertOrUpdateAllTags(
            listOf(tagEntity(id = "tag-a", name = "Tag A", createdAt = 1_000L))
        )

        // Act
        val tags = tagDao.observeTags().first()

        // Assert
        assertEquals(listOf("tag-a", "tag-b", "tag-c"), tags.map { it.id })
    }

    @Test
    fun observeActiveMemosWithRefsCreatedBetweenUsesInclusiveStartAndExclusiveEnd() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-before", createdAt = 999L))
        memoDao.upsertMemo(memoEntity(id = "memo-start", createdAt = 1_000L))
        memoDao.upsertMemo(memoEntity(id = "memo-end", createdAt = 2_000L))

        // Act
        val memos = memoDao.observeActiveMemosWithRefsCreatedBetween(
            fromMillis = 1_000L,
            toMillis = 2_000L
        ).first()

        // Assert
        assertEquals(listOf("memo-start"), memos.map { it.memo.id })
    }

    @Test
    fun observeActiveMemosWithRefsBySearchPatternMatchesTitleOrBody() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-title", title = "Trip plan"))
        memoDao.upsertMemo(memoEntity(id = "memo-body", body = "Trip notes"))
        memoDao.upsertMemo(memoEntity(id = "memo-other", title = "Shopping"))

        // Act
        val memos = memoDao.observeActiveMemosWithRefsBySearchPattern("%Trip%").first()

        // Assert
        assertEquals(listOf("memo-title", "memo-body"), memos.map { it.memo.id })
    }

    @Test
    fun observeActiveMemosWithRefsBySearchPatternMatchesAsciiIgnoringCase() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1", title = "Shopping list"))

        // Act
        val memos = memoDao.observeActiveMemosWithRefsBySearchPattern("%shopping%").first()

        // Assert
        assertEquals(listOf("memo-1"), memos.map { it.memo.id })
    }

    @Test
    fun observeActiveMemosWithRefsBySearchPatternTreatsEscapedWildcardsAsLiteral() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-literal", title = "100%_done"))
        memoDao.upsertMemo(memoEntity(id = "memo-wildcard", title = "100xxdone"))

        // Act
        val memos = memoDao.observeActiveMemosWithRefsBySearchPattern("%100\\%\\_done%").first()

        // Assert
        assertEquals(listOf("memo-literal"), memos.map { it.memo.id })
    }

    @Test
    fun observeActiveMemosWithRefsBySearchPatternReturnsRelatedTagRefs() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1", title = "Tagged memo"))
        tagDao.insertOrUpdateAllTags(listOf(tagEntity(id = "tag-1")))
        memoDao.insertTagRefs(
            listOf(
                MemoTagRefEntity(
                    memoId = "memo-1",
                    tagId = "tag-1",
                    position = 0
                )
            )
        )

        // Act
        val memo = memoDao.observeActiveMemosWithRefsBySearchPattern("%Tagged%").first().single()

        // Assert
        assertEquals(listOf("tag-1"), memo.tagRefs.map { it.tagId })
    }

    @Test
    fun observeActiveMemosWithRefsReturnsRelatedTagRefs() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        tagDao.insertOrUpdateAllTags(listOf(tagEntity(id = "tag-1")))
        memoDao.insertTagRefs(
            listOf(
                MemoTagRefEntity(
                    memoId = "memo-1",
                    tagId = "tag-1",
                    position = 0
                )
            )
        )

        // Act
        val memo = memoDao.observeActiveMemosWithRefs().first().single()

        // Assert
        assertEquals(listOf("tag-1"), memo.tagRefs.map { it.tagId })
    }

    @Test
    fun observeActiveMemosWithRefsReturnsRelatedImageRefs() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        memoDao.insertImageRefs(
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
            )
        )

        // Act
        val memo = memoDao.observeActiveMemosWithRefs().first().single()

        // Assert
        assertEquals(
            listOf("image-1.jpg", "image-2.png"),
            memo.imageRefs.sortedBy { it.position }.map { it.fileName }
        )
    }

    @Test
    fun upsertMemoWithRefsReplacesImageRefs() = runTest {
        // Arrange
        memoDao.upsertMemoWithRefs(
            memo = memoEntity(id = "memo-1"),
            tagRefs = emptyList(),
            imageRefs = listOf(
                MemoImageEntity(
                    id = "image-old",
                    memoId = "memo-1",
                    fileName = "old.jpg",
                    position = 0
                )
            )
        )

        // Act
        memoDao.upsertMemoWithRefs(
            memo = memoEntity(id = "memo-1"),
            tagRefs = emptyList(),
            imageRefs = listOf(
                MemoImageEntity(
                    id = "image-new",
                    memoId = "memo-1",
                    fileName = "new.jpg",
                    position = 0
                )
            )
        )
        val memo = memoDao.observeActiveMemosWithRefs().first().single()

        // Assert
        assertEquals(listOf("new.jpg"), memo.imageRefs.map { it.fileName })
    }

    @Test
    fun insertImageRefsThrowsWhenPositionsDuplicateForSameMemo() {
        assertThrows(SQLiteConstraintException::class.java) {
            runTest {
                // Arrange
                memoDao.upsertMemo(memoEntity(id = "memo-1"))
                val imageRefs = listOf(
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
                        position = 0
                    )
                )

                // Act
                memoDao.insertImageRefs(imageRefs)
            }
        }
    }

    @Test
    fun deleteTagCascadesDeleteToMemoTagRefs() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        tagDao.insertOrUpdateAllTags(listOf(tagEntity(id = "tag-1")))
        memoDao.insertTagRefs(
            listOf(
                MemoTagRefEntity(
                    memoId = "memo-1",
                    tagId = "tag-1",
                    position = 0
                )
            )
        )

        // Act
        tagDao.deleteTag("tag-1")

        // Assert
        assertEquals(
            emptyList<MemoTagRefEntity>(),
            memoDao.observeActiveMemosWithRefs().first().single().tagRefs
        )
    }

    @Test
    fun insertTagRefsThrowsWhenPositionsDuplicateForSameMemo() {
        assertThrows(SQLiteConstraintException::class.java) {
            runTest {
                // Arrange
                memoDao.upsertMemo(memoEntity(id = "memo-1"))
                tagDao.insertOrUpdateAllTags(listOf(tagEntity(id = "tag-1")))
                tagDao.insertOrUpdateAllTags(listOf(tagEntity(id = "tag-2")))
                val tagRefs = listOf(
                    MemoTagRefEntity(
                        memoId = "memo-1",
                        tagId = "tag-1",
                        position = 0
                    ),
                    MemoTagRefEntity(
                        memoId = "memo-1",
                        tagId = "tag-2",
                        position = 0
                    )
                )

                // Act
                memoDao.insertTagRefs(tagRefs)
            }
        }
    }

    @Test
    fun observeRecentActiveMemosOrdersByFavoriteThenUpdatedDesc() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "old", updatedAt = 1_000L))
        memoDao.upsertMemo(memoEntity(id = "new", updatedAt = 3_000L))
        memoDao.upsertMemo(memoEntity(id = "fav", updatedAt = 2_000L, isFavorite = true))
        memoDao.upsertMemo(memoEntity(id = "trashed", updatedAt = 4_000L, deletedAt = 5_000L))

        // Act
        val memos = memoDao.observeRecentActiveMemos(limit = 10).first()

        // Assert
        assertEquals(listOf("fav", "new", "old"), memos.map { it.id })
    }

    @Test
    fun observeRecentActiveMemosLimitsRowCount() = runTest {
        // Arrange
        (1..5).forEach { memoDao.upsertMemo(memoEntity(id = "memo-$it", updatedAt = it * 1_000L)) }

        // Act
        val memos = memoDao.observeRecentActiveMemos(limit = 2).first()

        // Assert
        assertEquals(listOf("memo-5", "memo-4"), memos.map { it.id })
    }

    @Test
    fun boundaryObserveRecentActiveMemosUsesIdAtLimitBoundary() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-c"))
        memoDao.upsertMemo(memoEntity(id = "memo-a"))
        memoDao.upsertMemo(memoEntity(id = "memo-b"))

        // Act
        // Boundary: identical sort keys use id ascending at the LIMIT boundary
        val memos = memoDao.observeRecentActiveMemos(limit = 2).first()

        // Assert
        assertEquals(listOf("memo-a", "memo-b"), memos.map { it.id })
    }

    @Test
    fun observeActiveMemosWithRefsExcludesTrashedMemos() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-active"))
        memoDao.upsertMemo(memoEntity(id = "memo-trashed", deletedAt = 2_000L))

        // Act
        val memos = memoDao.observeActiveMemosWithRefs().first()

        // Assert
        assertEquals(listOf("memo-active"), memos.map { it.memo.id })
    }

    @Test
    fun observeTrashedMemosWithRefsReturnsNewestDeletedFirst() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-old", deletedAt = 2_000L))
        memoDao.upsertMemo(memoEntity(id = "memo-active"))
        memoDao.upsertMemo(memoEntity(id = "memo-new", deletedAt = 3_000L))

        // Act
        val memos = memoDao.observeTrashedMemosWithRefs().first()

        // Assert
        assertEquals(listOf("memo-new", "memo-old"), memos.map { it.memo.id })
    }

    @Test
    fun restoreMemoFromTrashMovesMemoBackToActiveMemos() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1", deletedAt = 2_000L))

        // Act
        memoDao.restoreMemoFromTrash("memo-1")
        val memos = memoDao.observeActiveMemosWithRefs().first()

        // Assert
        assertEquals(listOf("memo-1"), memos.map { it.memo.id })
    }

    @Test
    fun boundaryGetActiveMemosWithRefsPreservesDistinctRequestedOrder() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        memoDao.upsertMemo(memoEntity(id = "memo-2"))

        // Act
        // Boundary: SQLite result order must not replace the caller's distinct ID order
        val memos = memoBulkDao.getActiveMemosWithRefs(
            listOf("memo-2", "memo-1", "memo-2")
        )

        // Assert
        assertEquals(listOf("memo-2", "memo-1"), memos.map { it.memo.id })
    }

    @Test
    fun boundaryMoveMemosToTrashSkipsAlreadyTrashedMembers() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-active"))
        memoDao.upsertMemo(memoEntity(id = "memo-trashed", deletedAt = 500L))

        // Act
        // Boundary: an already-trashed member is skipped while active members are trashed
        memoBulkDao.moveMemosToTrash(
            linkedMapOf(
                "memo-active" to 1_000L,
                "memo-trashed" to 2_000L
            )
        )
        val activeIds = memoDao.observeActiveMemosWithRefs().first().map { it.memo.id }
        val trashedIds = memoDao.observeTrashedMemosWithRefs().first().map { it.memo.id }

        // Assert
        assertEquals(
            emptyList<String>() to listOf("memo-active", "memo-trashed"),
            activeIds to trashedIds.sorted()
        )
    }

    @Test
    fun errorMoveMemosToTrashRollsBackEarlierUpdatesWhenLaterWriteFails() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        memoDao.upsertMemo(memoEntity(id = "memo-2"))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_second_bulk_trash_update
            BEFORE UPDATE OF deletedAt ON memos
            WHEN OLD.id = 'memo-2'
            BEGIN
                SELECT RAISE(ABORT, 'forced bulk update failure');
            END
            """.trimIndent()
        )

        // Act
        // Error: a real SQLite failure rolls the transaction back to its pre-call state
        val error = runCatching {
            memoBulkDao.moveMemosToTrash(
                linkedMapOf(
                    "memo-1" to 1_000L,
                    "memo-2" to 2_000L
                )
            )
        }.exceptionOrNull()
        val activeIds = memoDao.observeActiveMemosWithRefs()
            .first()
            .map { it.memo.id }
            .sorted()

        // Assert
        assertEquals(
            SQLiteConstraintException::class.java to listOf("memo-1", "memo-2"),
            error?.javaClass to activeIds
        )
    }

    @Test
    fun errorSaveAllActiveMemosRejectsMixedStateBeforeReplacingAnyMemo() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-active", title = "Active before"))
        memoDao.upsertMemo(
            memoEntity(id = "memo-trashed", title = "Trashed before", deletedAt = 500L)
        )

        // Act
        // Error: every memo must still be active before any replacement begins
        val error = runCatching {
            memoBulkDao.upsertAllActiveMemosWithRefsAndCollectRemovedFileNames(
                expectedActiveIds = listOf("memo-active", "memo-trashed"),
                memos = listOf(
                    memoEntity(id = "memo-active", title = "Active after")
                ),
                tagRefsByMemoId = emptyMap(),
                imageRefsByMemoId = emptyMap()
            )
        }.exceptionOrNull()
        val activeTitle = memoDao.observeActiveMemosWithRefs().first().single().memo.title

        // Assert
        assertEquals(
            IllegalStateException::class.java to "Active before",
            error?.javaClass to activeTitle
        )
    }

    @Test
    fun errorRestoreMemosFromTrashRejectsMixedStateBeforeWriting() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-trashed", deletedAt = 500L))
        memoDao.upsertMemo(memoEntity(id = "memo-active"))

        // Act
        // Error: an active member rejects the whole restore operation
        val error = runCatching {
            memoBulkDao.restoreMemosFromTrash(listOf("memo-trashed", "memo-active"))
        }.exceptionOrNull()
        val trashedIds = memoDao.observeTrashedMemosWithRefs().first().map { it.memo.id }

        // Assert
        assertEquals(
            IllegalStateException::class.java to listOf("memo-trashed"),
            error?.javaClass to trashedIds
        )
    }

    @Test
    fun errorDeleteMemosPermanentlyRejectsMixedStateBeforeWriting() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-trashed", deletedAt = 500L))
        memoDao.upsertMemo(memoEntity(id = "memo-active"))

        // Act
        // Error: an active member rejects the whole permanent delete operation
        val error = runCatching {
            memoBulkDao.deleteMemosPermanentlyAndCollectImageFileNames(
                listOf("memo-trashed", "memo-active")
            )
        }.exceptionOrNull()
        val remainingIds = (
            memoDao.observeActiveMemosWithRefs().first() +
                memoDao.observeTrashedMemosWithRefs().first()
            ).map { it.memo.id }.sorted()

        // Assert
        assertEquals(
            IllegalStateException::class.java to listOf("memo-active", "memo-trashed"),
            error?.javaClass to remainingIds
        )
    }

    @Test
    fun normalDeleteMemosPermanentlyCollectsImageNamesAndDeletesRows() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1", deletedAt = 1_000L))
        memoDao.upsertMemo(memoEntity(id = "memo-2", deletedAt = 2_000L))
        memoDao.insertImageRefs(
            listOf(
                MemoImageEntity(
                    id = "image-1",
                    memoId = "memo-1",
                    fileName = "image-1.jpg",
                    position = 0
                ),
                MemoImageEntity(
                    id = "image-2",
                    memoId = "memo-2",
                    fileName = "image-2.jpg",
                    position = 0
                )
            )
        )

        // Act
        // Normal: the transaction returns cleanup inputs after deleting every selected row
        val fileNames = memoBulkDao.deleteMemosPermanentlyAndCollectImageFileNames(
            listOf("memo-2", "memo-1")
        )
        val trashedIds = memoDao.observeTrashedMemosWithRefs().first().map { it.memo.id }

        // Assert
        assertEquals(
            setOf("image-1.jpg", "image-2.jpg") to emptyList<String>(),
            fileNames.toSet() to trashedIds
        )
    }

    @Test
    fun deleteMemoPermanentlyDoesNotDeleteActiveMemo() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))

        // Act
        val deletedCount = memoDao.deleteMemoPermanently("memo-1")

        // Assert
        assertEquals(0, deletedCount)
    }

    @Test
    fun deleteMemoPermanentlyDeletesTrashedMemo() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1", deletedAt = 2_000L))

        // Act
        memoDao.deleteMemoPermanently("memo-1")
        val memos = memoDao.observeTrashedMemosWithRefs().first()

        // Assert
        assertEquals(0, memos.size)
    }

    @Test
    fun discardMemoDeletesActiveMemo() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))

        // Act
        val deletedCount = memoDao.discardMemo("memo-1")

        // Assert
        assertEquals(1, deletedCount)
    }

    @Test
    fun discardMemoCascadesDeleteToMemoTagRefs() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        tagDao.insertOrUpdateAllTags(listOf(tagEntity(id = "tag-1")))
        memoDao.insertTagRefs(
            listOf(MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0))
        )

        // Act
        memoDao.discardMemo("memo-1")
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        val memo = memoDao.observeActiveMemosWithRefs().first().single()

        // Assert
        assertEquals(emptyList<String>(), memo.tagRefs.map { it.tagId })
    }

    @Test
    fun discardMemoCascadesDeleteToMemoImageRefs() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        memoDao.insertImageRefs(
            listOf(
                MemoImageEntity(
                    id = "image-1",
                    memoId = "memo-1",
                    fileName = "image-1.jpg",
                    position = 0
                )
            )
        )

        // Act
        memoDao.discardMemo("memo-1")
        memoDao.upsertMemo(memoEntity(id = "memo-1"))
        val memo = memoDao.observeActiveMemosWithRefs().first().single()

        // Assert
        assertEquals(emptyList<String>(), memo.imageRefs.map { it.fileName })
    }

    @Test
    fun discardMemoReturnsZeroWhenMemoDoesNotExist() = runTest {
        // Arrange
        val missingId = "missing"

        // Act
        val deletedCount = memoDao.discardMemo(missingId)

        // Assert
        assertEquals(0, deletedCount)
    }

    @Test
    fun deleteTrashedMemosDeletedAtOrBeforeUsesInclusiveCutoff() = runTest {
        // Arrange
        memoDao.upsertMemo(memoEntity(id = "memo-old", deletedAt = 1_000L))
        memoDao.upsertMemo(memoEntity(id = "memo-new", deletedAt = 1_001L))

        // Act
        memoDao.deleteTrashedMemosDeletedAtOrBefore(1_000L)
        val memos = memoDao.observeTrashedMemosWithRefs().first()

        // Assert
        assertEquals(listOf("memo-new"), memos.map { it.memo.id })
    }

    private fun memoEntity(
        id: String,
        title: String = "Title",
        body: String = "Body",
        createdAt: Long = 1_000L,
        updatedAt: Long = createdAt,
        isFavorite: Boolean = false,
        deletedAt: Long? = null
    ) = MemoEntity(
        id = id,
        title = title,
        body = body,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite,
        deletedAt = deletedAt
    )

    private fun tagEntity(id: String, name: String = "Tag", createdAt: Long = 1_000L) = TagEntity(
        id = id,
        name = name,
        colorArgb = 0xFF6750A4,
        createdAt = createdAt
    )
}
