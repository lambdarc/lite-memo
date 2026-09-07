package com.lambdarc.litememo.data.repository

import com.lambdarc.litememo.data.local.dao.MemoBulkDao
import com.lambdarc.litememo.data.local.entity.MemoEntity
import com.lambdarc.litememo.data.local.entity.MemoImageEntity
import com.lambdarc.litememo.data.local.entity.MemoTagRefEntity
import com.lambdarc.litememo.data.local.model.MemoSummaryProjection
import com.lambdarc.litememo.data.local.model.MemoWithRefs
import com.lambdarc.litememo.data.mapper.toDomain
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.memoSummaryFixture
import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.MemoTrashUpdate
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.SearchQuery
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.model.value.TimestampRange
import com.lambdarc.litememo.domain.repository.MemoImageStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class RoomMemoRepositoryTest {

    @Test
    fun observeActiveMemosReturnsDomainMemosFromDao() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(
                    memoId = "memo-1",
                    tagRefs = listOf(
                        MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0)
                    )
                )
            )
        )
        val repository = createRepository(dao)

        // Act
        val memos = repository.observeActiveMemos().first()

        // Assert
        assertEquals(listOf(MemoId("memo-1")), memos.map { it.id })
    }

    @Test
    fun normalObserveRecentActiveMemosReturnsSummaryContract() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(
                    memoId = "memo-1",
                    title = "Title",
                    body = "Body",
                    isFavorite = true,
                    tagRefs = listOf(
                        MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0)
                    ),
                    imageRefs = listOf(
                        MemoImageEntity(
                            id = "image-1",
                            memoId = "memo-1",
                            fileName = "image-1.jpg",
                            position = 0
                        )
                    )
                )
            )
        )
        val repository = createRepository(dao)

        // Act
        // Normal: memo-only rows map to the explicit summary contract
        val summaries = repository.observeRecentActiveMemos(limit = 8).first()

        // Assert
        assertEquals(
            listOf(
                memoSummaryFixture(
                    id = "memo-1",
                    title = "Title",
                    body = "Body",
                    isFavorite = true
                )
            ),
            summaries
        )
    }

    @Test
    fun observeActiveMemosBySearchQueryDelegatesEscapedLikePatternToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.observeActiveMemosBySearchQuery(SearchQuery("100%_\\")).first()

        // Assert
        assertEquals("%100\\%\\_\\\\%", dao.observedSearchPattern)
    }

    @Test
    fun observeActiveMemosBySearchQueryReturnsDomainMemosFromDao() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(
                    memoId = "memo-1",
                    tagRefs = listOf(
                        MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0)
                    )
                )
            )
        )
        val repository = createRepository(dao)

        // Act
        val memos = repository.observeActiveMemosBySearchQuery(SearchQuery("title")).first()

        // Assert
        assertEquals(listOf(MemoId("memo-1")), memos.map { it.id })
    }

    @Test
    fun interactionObserveActiveMemosCreatedBetweenDelegatesTimestampValuesToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        // Interaction: repository unwraps the range only at the DAO boundary
        repository.observeActiveMemosCreatedBetween(
            TimestampRange(
                fromInclusive = TimestampMillis(1_000L),
                toExclusive = TimestampMillis(2_000L)
            )
        ).first()

        // Assert
        assertEquals(ObservedRange(fromMillis = 1_000L, toMillis = 2_000L), dao.observedRange)
    }

    @Test
    fun normalObserveActiveMemosCreatedBetweenReturnsDomainMemosFromDao() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(memoWithRefs(memoId = "memo-1"))
        )
        val repository = createRepository(dao)

        // Act
        // Normal: DAO results are mapped back to domain memos
        val memos = repository.observeActiveMemosCreatedBetween(
            TimestampRange(
                fromInclusive = TimestampMillis(500L),
                toExclusive = TimestampMillis(2_000L)
            )
        ).first()

        // Assert
        assertEquals(listOf(MemoId("memo-1")), memos.map { it.id })
    }

    @Test
    fun boundaryObserveActiveMemosCreatedBetweenUsesInclusiveStartAndExclusiveEnd() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-before", createdAt = 999L),
                memoWithRefs(memoId = "memo-start", createdAt = 1000L),
                memoWithRefs(memoId = "memo-end", createdAt = 2000L),
                memoWithRefs(memoId = "memo-after", createdAt = 2001L)
            )
        )
        val repository = createRepository(dao)

        // Act
        // Boundary: start is included and end is excluded
        val memos = repository.observeActiveMemosCreatedBetween(
            TimestampRange(
                fromInclusive = TimestampMillis(1_000L),
                toExclusive = TimestampMillis(2_000L)
            )
        ).first()

        // Assert
        assertEquals(listOf(MemoId("memo-start")), memos.map { it.id })
    }

    @Test
    fun boundaryObserveActiveMemosCreatedBetweenShortCircuitsEqualRange() = runTest {
        // Arrange
        val dao = FakeMemoDao(failOnObserveMemosBetween = true)
        val repository = createRepository(dao)

        // Act
        // Boundary/Interaction: an empty range emits empty without querying Room
        val memos = repository.observeActiveMemosCreatedBetween(
            TimestampRange(
                fromInclusive = TimestampMillis(1_000L),
                toExclusive = TimestampMillis(1_000L)
            )
        ).first()

        // Assert
        assertEquals(emptyList<MemoId>(), memos.map { it.id })
    }

    @Test
    fun getActiveMemoReturnsNullWhenDaoReturnsNull() = runTest {
        // Arrange
        val repository = createRepository(FakeMemoDao())

        // Act
        val memo = repository.getActiveMemo(MemoId("missing"))

        // Assert
        assertNull(memo)
    }

    @Test
    fun boundaryGetActiveMemosReturnsDistinctMemosInRequestedOrder() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-1"),
                memoWithRefs(memoId = "memo-2"),
                memoWithRefs(memoId = "memo-trashed", deletedAt = 2_000L)
            )
        )
        val repository = createRepository(dao)

        // Act
        // Boundary: duplicate IDs are removed while requested order remains stable
        val memos = repository.getActiveMemos(
            listOf(MemoId("memo-2"), MemoId("memo-1"), MemoId("memo-2"))
        )

        // Assert
        assertEquals(listOf(MemoId("memo-2"), MemoId("memo-1")), memos.map { it.id })
    }

    @Test
    fun boundaryGetActiveMemosSkipsDaoForEmptyInput() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        // Boundary/Interaction: empty input is a repository-level no-op
        val memos = repository.getActiveMemos(emptyList())

        // Assert
        assertAll(
            { assertEquals(emptyList<MemoId>(), memos.map { it.id }) },
            { assertEquals(0, dao.activeMemoReadCount) }
        )
    }

    @Test
    fun saveMemoWritesMemoEntityToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.saveMemo(memoFixture(id = "memo-1", title = "Title"))

        // Assert
        assertEquals("memo-1", dao.savedMemo?.id)
    }

    @Test
    fun saveMemoWritesTagRefsToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.saveMemo(
            memoFixture(
                id = "memo-1",
                tagIds = listOf(TagId("tag-1"), TagId("tag-2"))
            )
        )

        // Assert
        assertEquals(
            listOf(
                MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0),
                MemoTagRefEntity(memoId = "memo-1", tagId = "tag-2", position = 1)
            ),
            dao.savedTagRefs
        )
    }

    @Test
    fun interactionSaveMemoWritesImageRefsToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        // Interaction: image order is delegated to DAO refs.
        repository.saveMemo(
            memoFixture(
                id = "memo-1",
                images = listOf(
                    memoImageFixture(id = "image-1", fileName = "image-1.jpg"),
                    memoImageFixture(id = "image-2", fileName = "image-2.png")
                )
            )
        )

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
            dao.savedImageRefs
        )
    }

    @Test
    fun interactionSaveMemoDeletesRemovedImageFilesAfterUpsert() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            imageFileNamesByMemoId = mutableMapOf("memo-1" to listOf("old.jpg", "keep.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Interaction: files removed from DB refs are deleted after memo upsert.
        repository.saveMemo(
            memoFixture(
                id = "memo-1",
                images = listOf(memoImageFixture(id = "image-keep", fileName = "keep.jpg"))
            )
        )

        // Assert
        assertEquals(listOf(MemoImageFileName("old.jpg")), imageStore.deletedFileNames)
    }

    @Test
    fun interactionSaveMemoDoesNotDeleteFilesWhenImagesUnchanged() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            imageFileNamesByMemoId = mutableMapOf("memo-1" to listOf("keep.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Interaction: unchanged image refs do not touch file storage.
        repository.saveMemo(
            memoFixture(
                id = "memo-1",
                images = listOf(memoImageFixture(id = "image-keep", fileName = "keep.jpg"))
            )
        )

        // Assert
        assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames)
    }

    @Test
    fun observeTrashedMemosReturnsOnlyTrashedMemosOrderedByDeletedAtDescending() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-active"),
                memoWithRefs(memoId = "memo-old", deletedAt = 1_000L),
                memoWithRefs(memoId = "memo-new", deletedAt = 2_000L)
            )
        )
        val repository = createRepository(dao)

        // Act
        val memos = repository.observeTrashedMemos().first()

        // Assert
        assertEquals(listOf(MemoId("memo-new"), MemoId("memo-old")), memos.map { it.id })
    }

    @Test
    fun moveMemoToTrashDelegatesMemoIdAndDeletedAtValuesToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.moveMemoToTrash(MemoId("memo-1"), TimestampMillis(2_000L))

        // Assert
        assertEquals(MovedToTrashRecord(memoId = "memo-1", deletedAt = 2_000L), dao.movedToTrash)
    }

    @Test
    fun restoreMemoFromTrashDelegatesMemoIdValueToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.restoreMemoFromTrash(MemoId("memo-1"))

        // Assert
        assertEquals("memo-1", dao.restoredMemoId)
    }

    @Test
    fun interactionMoveMemosToTrashTrashesActiveMemosGroupedByDeletedAt() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-1"),
                memoWithRefs(memoId = "memo-2")
            )
        )
        val repository = createRepository(dao)

        // Act
        // Interaction: every still-active memo is trashed with its requested deletedAt
        repository.moveMemosToTrash(
            listOf(
                MemoTrashUpdate(MemoId("memo-2"), TimestampMillis(2_000L)),
                MemoTrashUpdate(MemoId("memo-1"), TimestampMillis(1_000L))
            )
        )

        // Assert
        assertEquals(
            setOf(
                MovedToTrashRecord(memoId = "memo-2", deletedAt = 2_000L),
                MovedToTrashRecord(memoId = "memo-1", deletedAt = 1_000L)
            ),
            dao.movedToTrashRecords.toSet()
        )
    }

    @Test
    fun errorMoveMemosToTrashRejectsDuplicateMemoIds() {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(memoWithRefs(memoId = "memo-1"))
        )
        val repository = createRepository(dao)

        // Act & Assert
        // Error: duplicate memo ids in one bulk request are a caller bug.
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                repository.moveMemosToTrash(
                    listOf(
                        MemoTrashUpdate(MemoId("memo-1"), TimestampMillis(1_000L)),
                        MemoTrashUpdate(MemoId("memo-1"), TimestampMillis(2_000L))
                    )
                )
            }
        }
    }

    @Test
    fun boundaryMoveMemosToTrashSkipsMemosNoLongerActive() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-active"),
                memoWithRefs(memoId = "memo-trashed", deletedAt = 500L)
            )
        )
        val repository = createRepository(dao)

        // Act
        // Boundary: a concurrently trashed member is skipped instead of failing the batch
        repository.moveMemosToTrash(
            listOf(
                MemoTrashUpdate(MemoId("memo-active"), TimestampMillis(1_000L)),
                MemoTrashUpdate(MemoId("memo-trashed"), TimestampMillis(2_000L))
            )
        )

        // Assert
        assertEquals(
            listOf(MovedToTrashRecord(memoId = "memo-active", deletedAt = 1_000L)),
            dao.movedToTrashRecords
        )
    }

    @Test
    fun moveMemoToTrashThrowsWhenDaoDoesNotMoveMemo() {
        // Arrange
        val dao = FakeMemoDao(movedToTrashCount = 0)
        val repository = createRepository(dao)

        // Act & Assert
        // Error: DAO zero affected rows are exposed as an illegal state.
        assertThrows(IllegalStateException::class.java) {
            runTest { repository.moveMemoToTrash(MemoId("memo-1"), TimestampMillis(2_000L)) }
        }
    }

    @Test
    fun restoreMemoFromTrashThrowsWhenDaoDoesNotRestoreMemo() {
        // Arrange
        val dao = FakeMemoDao(restoredCount = 0)
        val repository = createRepository(dao)

        // Act & Assert
        // Error: DAO zero affected rows are exposed as an illegal state.
        assertThrows(IllegalStateException::class.java) {
            runTest { repository.restoreMemoFromTrash(MemoId("memo-1")) }
        }
    }

    @Test
    fun deleteMemoPermanentlyDelegatesMemoIdValueToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.deleteMemoPermanently(MemoId("memo-1"))

        // Assert
        assertEquals("memo-1", dao.permanentlyDeletedMemoId)
    }

    @Test
    fun deleteMemoPermanentlyThrowsWhenDaoDoesNotDeleteMemo() {
        // Arrange
        val dao = FakeMemoDao(deletedPermanentlyCount = 0)
        val repository = createRepository(dao)

        // Act & Assert
        assertThrows(IllegalStateException::class.java) {
            runTest { repository.deleteMemoPermanently(MemoId("memo-1")) }
        }
    }

    @Test
    fun interactionDeleteMemoPermanentlyDeletesImageFiles() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            imageFileNamesByMemoId = mutableMapOf("memo-1" to listOf("image-1.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Interaction: permanent delete removes files after deleting DB rows.
        repository.deleteMemoPermanently(MemoId("memo-1"))

        // Assert
        assertEquals(listOf(MemoImageFileName("image-1.jpg")), imageStore.deletedFileNames)
    }

    @Test
    fun errorDeleteMemoPermanentlyDoesNotDeleteFilesWhenMemoMissing() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            deletedPermanentlyCount = 0,
            imageFileNamesByMemoId = mutableMapOf("memo-1" to listOf("image-1.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Error: failed DB delete must not remove files.
        val error = runCatching { repository.deleteMemoPermanently(MemoId("memo-1")) }
            .exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames) }
        )
    }

    @Test
    fun discardMemoDelegatesMemoIdValueToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        // Interaction: discard uses the unconditional DAO delete path.
        repository.discardMemo(MemoId("memo-1"))

        // Assert
        assertEquals("memo-1", dao.discardedMemoId)
    }

    @Test
    fun discardMemoDoesNotThrowWhenDaoDoesNotDeleteMemo() = runTest {
        // Arrange
        val dao = FakeMemoDao().apply { discardedCount = 0 }
        val repository = createRepository(dao)

        // Act
        // Boundary: missing abandoned rows are treated as no-op.
        val error = runCatching { repository.discardMemo(MemoId("missing")) }.exceptionOrNull()

        // Assert
        assertNull(error)
    }

    @Test
    fun interactionDiscardMemoDeletesImageFiles() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            imageFileNamesByMemoId = mutableMapOf("memo-1" to listOf("image-1.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Interaction: discard removes files after deleting DB rows.
        repository.discardMemo(MemoId("memo-1"))

        // Assert
        assertEquals(listOf(MemoImageFileName("image-1.jpg")), imageStore.deletedFileNames)
    }

    @Test
    fun deleteTrashedMemosDeletedAtOrBeforeDelegatesCutoffValueToDao() = runTest {
        // Arrange
        val dao = FakeMemoDao()
        val repository = createRepository(dao)

        // Act
        repository.deleteTrashedMemosDeletedAtOrBefore(TimestampMillis(2_000L))

        // Assert
        assertEquals(2_000L, dao.purgeCutoff)
    }

    @Test
    fun interactionPurgeDeletesImageFilesOfExpiredTrashedMemos() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            imageFileNamesForPurge = listOf("old-1.jpg", "old-2.jpg")
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Interaction: purge uses the DAO-provided file list after the transaction.
        repository.deleteTrashedMemosDeletedAtOrBefore(TimestampMillis(2_000L))

        // Assert
        assertEquals(
            listOf(MemoImageFileName("old-1.jpg"), MemoImageFileName("old-2.jpg")),
            imageStore.deletedFileNames
        )
    }

    @Test
    fun interactionSaveActiveMemoBulkWritesDeletesRemovedImageFilesAfterDatabaseSuccess() =
        runTest {
            // Arrange
            val dao = FakeMemoDao(
                memosWithRefs = listOf(memoWithRefs(memoId = "memo-1", updatedAt = 1_000L)),
                imageFileNamesByMemoId = mutableMapOf(
                    "memo-1" to listOf("old.jpg", "keep.jpg")
                )
            )
            val imageStore = FakeMemoImageStore()
            val repository = createRepository(dao, imageStore)

            // Act
            // Interaction: file cleanup starts only after the active-only transaction returns
            repository.saveActiveMemoBulkWrites(
                listOf(
                    ActiveMemoBulkWrite.Update(
                        expectedMemo = memoWithRefs(
                            memoId = "memo-1",
                            updatedAt = 1_000L
                        ).toDomain(),
                        updatedMemo = memoFixture(
                            id = "memo-1",
                            updatedAt = 1_000L,
                            images = listOf(
                                memoImageFixture(id = "image-keep", fileName = "keep.jpg")
                            )
                        )
                    )
                )
            )

            // Assert
            assertEquals(listOf(MemoImageFileName("old.jpg")), imageStore.deletedFileNames)
        }

    @Test
    fun errorSaveActiveMemoBulkWritesDoesNotDeleteFilesWhenVersionCheckFails() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(memoWithRefs(memoId = "memo-1", updatedAt = 5_000L)),
            imageFileNamesByMemoId = mutableMapOf("memo-1" to listOf("old.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Error: a stale expected version aborts the transaction before file cleanup
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(
                    ActiveMemoBulkWrite.Update(
                        expectedMemo = memoWithRefs(
                            memoId = "memo-1",
                            updatedAt = 1_000L
                        ).toDomain(),
                        updatedMemo = memoFixture(id = "memo-1", updatedAt = 6_000L)
                    )
                )
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames) }
        )
    }

    @Test
    fun errorSaveActiveMemoBulkWritesDoesNotDeleteFilesWhenMemoNoLongerActive() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            imageFileNamesByMemoId = mutableMapOf("memo-missing" to listOf("old.jpg"))
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Error: an absent active memo aborts the transaction before file cleanup
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(
                    ActiveMemoBulkWrite.CheckOnly(
                        expectedMemo = memoWithRefs(
                            memoId = "memo-missing",
                            updatedAt = 1_000L
                        ).toDomain()
                    )
                )
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames) }
        )
    }

    @Test
    fun interactionDeleteMemosPermanentlyDeletesImageFilesAfterDatabaseSuccess() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-1", deletedAt = 1_000L),
                memoWithRefs(memoId = "memo-2", deletedAt = 2_000L)
            ),
            imageFileNamesByMemoId = mutableMapOf(
                "memo-1" to listOf("image-1.jpg"),
                "memo-2" to listOf("image-2.jpg")
            )
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Interaction: files collected by the delete transaction are removed after it returns
        repository.deleteMemosPermanently(listOf(MemoId("memo-2"), MemoId("memo-1")))

        // Assert
        assertEquals(
            listOf(MemoImageFileName("image-2.jpg"), MemoImageFileName("image-1.jpg")),
            imageStore.deletedFileNames
        )
    }

    @Test
    fun boundaryDeleteMemosPermanentlySkipsMemosNoLongerInTrash() = runTest {
        // Arrange
        val dao = FakeMemoDao(
            memosWithRefs = listOf(
                memoWithRefs(memoId = "memo-trashed", deletedAt = 1_000L),
                memoWithRefs(memoId = "memo-active")
            ),
            imageFileNamesByMemoId = mutableMapOf(
                "memo-trashed" to listOf("trashed.jpg"),
                "memo-active" to listOf("active.jpg")
            )
        )
        val imageStore = FakeMemoImageStore()
        val repository = createRepository(dao, imageStore)

        // Act
        // Boundary: only currently-trashed members are deleted and their files removed
        repository.deleteMemosPermanently(
            listOf(MemoId("memo-trashed"), MemoId("memo-active"))
        )

        // Assert
        assertEquals(
            listOf(MemoImageFileName("trashed.jpg")),
            imageStore.deletedFileNames
        )
    }

    private fun createRepository(
        dao: FakeMemoDao,
        imageStore: FakeMemoImageStore = FakeMemoImageStore()
    ): RoomMemoRepository = RoomMemoRepository(dao, dao, imageStore)

    private fun memoWithRefs(
        memoId: String,
        title: String = "Title",
        body: String = "Body",
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        isFavorite: Boolean = false,
        tagRefs: List<MemoTagRefEntity> = emptyList(),
        imageRefs: List<MemoImageEntity> = emptyList(),
        deletedAt: Long? = null
    ) = MemoWithRefs(
        memo = MemoEntity(
            id = memoId,
            title = title,
            body = body,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isFavorite = isFavorite,
            deletedAt = deletedAt
        ),
        tagRefs = tagRefs,
        imageRefs = imageRefs
    )

    private data class MovedToTrashRecord(val memoId: String, val deletedAt: Long)

    private data class ObservedRange(val fromMillis: Long, val toMillis: Long)

    private class FakeMemoDao(
        memosWithRefs: List<MemoWithRefs> = emptyList(),
        private val imageFileNamesByMemoId: MutableMap<String, List<String>> = mutableMapOf(),
        private val imageFileNamesForPurge: List<String> = emptyList(),
        private val failOnObserveMemosBetween: Boolean = false,
        private val movedToTrashCount: Int = 1,
        private val restoredCount: Int = 1,
        private val deletedPermanentlyCount: Int = 1
    ) : MemoBulkDao {

        private val memosWithRefs = MutableStateFlow(memosWithRefs)
        var savedMemo: MemoEntity? = null
        var savedTagRefs: List<MemoTagRefEntity> = emptyList()
        var savedImageRefs: List<MemoImageEntity> = emptyList()
        var movedToTrash: MovedToTrashRecord? = null
        var restoredMemoId: String? = null
        var permanentlyDeletedMemoId: String? = null
        var discardedMemoId: String? = null
        var discardedCount: Int = 1
        var purgeCutoff: Long? = null
        var observedRange: ObservedRange? = null
        var observedSearchPattern: String? = null
        var activeMemoReadCount: Int = 0
        val movedToTrashRecords = mutableListOf<MovedToTrashRecord>()

        override fun observeActiveMemosWithRefs(): Flow<List<MemoWithRefs>> =
            memosWithRefs.map { list -> list.filter { it.memo.deletedAt == null } }

        override fun observeRecentActiveMemos(limit: Int): Flow<List<MemoSummaryProjection>> =
            memosWithRefs.map { list ->
                list.map { it.memo }
                    .filter { it.deletedAt == null }
                    .sortedWith(
                        compareByDescending<MemoEntity> { it.isFavorite }
                            .thenByDescending { it.updatedAt }
                            .thenByDescending { it.createdAt }
                    )
                    .take(limit)
                    .map { memo ->
                        MemoSummaryProjection(
                            id = memo.id,
                            title = memo.title,
                            body = memo.body,
                            isFavorite = memo.isFavorite
                        )
                    }
            }

        override fun observeActiveMemosWithRefsBySearchPattern(
            pattern: String
        ): Flow<List<MemoWithRefs>> {
            observedSearchPattern = pattern
            return memosWithRefs.map { list -> list.filter { it.memo.deletedAt == null } }
        }

        override fun observeActiveMemosWithRefsCreatedBetween(
            fromMillis: Long,
            toMillis: Long
        ): Flow<List<MemoWithRefs>> {
            if (failOnObserveMemosBetween) {
                fail<Nothing>("observeActiveMemosWithRefsCreatedBetween should not be called.")
            }
            observedRange = ObservedRange(fromMillis = fromMillis, toMillis = toMillis)
            return memosWithRefs.map { list ->
                list.filter {
                    it.memo.deletedAt == null &&
                        it.memo.createdAt >= fromMillis &&
                        it.memo.createdAt < toMillis
                }
            }
        }

        override suspend fun getActiveMemoWithRefs(id: String): MemoWithRefs? =
            memosWithRefs.value.firstOrNull { it.memo.id == id && it.memo.deletedAt == null }

        override suspend fun getActiveMemosWithRefsBatch(ids: List<String>): List<MemoWithRefs> {
            activeMemoReadCount += 1
            // Roomの行順は保証されないため、意図的に反転して入力順への復元を検証する。
            return memosWithRefs.value
                .filter { it.memo.id in ids && it.memo.deletedAt == null }
                .asReversed()
        }

        override suspend fun getActiveMemoIdsBatch(ids: List<String>): List<String> =
            memosWithRefs.value
                .filter { it.memo.id in ids && it.memo.deletedAt == null }
                .map { it.memo.id }

        override suspend fun getImageRefsForMemosBatch(
            memoIds: List<String>
        ): List<MemoImageEntity> = memosWithRefs.value
            .filter { it.memo.id in memoIds }
            .flatMap { it.imageRefs }

        override suspend fun getTrashedMemoIdsBatch(ids: List<String>): List<String> =
            memosWithRefs.value
                .filter { it.memo.id in ids && it.memo.deletedAt != null }
                .map { it.memo.id }

        override fun observeTrashedMemosWithRefs(): Flow<List<MemoWithRefs>> =
            memosWithRefs.map { list ->
                list
                    .filter { it.memo.deletedAt != null }
                    .sortedByDescending { it.memo.deletedAt }
            }

        override suspend fun upsertMemoWithRefs(
            memo: MemoEntity,
            tagRefs: List<MemoTagRefEntity>,
            imageRefs: List<MemoImageEntity>
        ) {
            savedMemo = memo
            savedTagRefs = tagRefs
            savedImageRefs = imageRefs
            imageFileNamesByMemoId[memo.id] = imageRefs.map { it.fileName }
        }

        override suspend fun upsertMemo(memo: MemoEntity) {
            savedMemo = memo
        }

        override suspend fun insertTagRefs(tagRefs: List<MemoTagRefEntity>) {
            savedTagRefs = tagRefs
        }

        override suspend fun insertImageRefs(imageRefs: List<MemoImageEntity>) {
            savedImageRefs = imageRefs
        }

        override suspend fun deleteTagRefsForMemo(memoId: String) {
            savedTagRefs = emptyList()
        }

        override suspend fun deleteImageRefsForMemo(memoId: String) {
            savedImageRefs = emptyList()
            imageFileNamesByMemoId[memoId] = emptyList()
        }

        override suspend fun getImageFileNamesForMemo(memoId: String): List<String> =
            imageFileNamesByMemoId[memoId].orEmpty()

        override suspend fun getImageFileNamesForMemos(memoIds: List<String>): List<String> =
            memoIds.flatMap { imageFileNamesByMemoId[it].orEmpty() }

        override suspend fun findReferencedImageFileNames(fileNames: List<String>): List<String> =
            imageFileNamesByMemoId.values.flatten().filter { it in fileNames }

        override suspend fun getImageFileNamesForTrashedMemosDeletedAtOrBefore(
            cutoff: Long
        ): List<String> = imageFileNamesForPurge

        override suspend fun moveMemoToTrash(id: String, deletedAt: Long): Int {
            movedToTrash = MovedToTrashRecord(memoId = id, deletedAt = deletedAt)
            movedToTrashRecords += requireNotNull(movedToTrash)
            return movedToTrashCount
        }

        override suspend fun moveMemosToTrashBatch(ids: List<String>, deletedAt: Long): Int {
            val movableIds = memosWithRefs.value
                .filter { it.memo.id in ids && it.memo.deletedAt == null }
                .map { it.memo.id }
                .toSet()
            movableIds.forEach { id ->
                movedToTrashRecords += MovedToTrashRecord(memoId = id, deletedAt = deletedAt)
            }
            memosWithRefs.value = memosWithRefs.value.map { memo ->
                if (memo.memo.id in movableIds) {
                    memo.copy(memo = memo.memo.copy(deletedAt = deletedAt))
                } else {
                    memo
                }
            }
            return movableIds.size
        }

        override suspend fun restoreMemoFromTrash(id: String): Int {
            restoredMemoId = id
            return restoredCount
        }

        override suspend fun restoreMemosFromTrashBatch(ids: List<String>): Int {
            val restoredIds = memosWithRefs.value
                .filter { it.memo.id in ids && it.memo.deletedAt != null }
                .map { it.memo.id }
                .toSet()
            memosWithRefs.value = memosWithRefs.value.map { memo ->
                if (memo.memo.id in restoredIds) {
                    memo.copy(memo = memo.memo.copy(deletedAt = null))
                } else {
                    memo
                }
            }
            return restoredIds.size
        }

        override suspend fun deleteMemoPermanently(id: String): Int {
            permanentlyDeletedMemoId = id
            if (deletedPermanentlyCount > 0) {
                imageFileNamesByMemoId.remove(id)
            }
            return deletedPermanentlyCount
        }

        override suspend fun deleteMemosPermanentlyBatch(ids: List<String>): Int {
            val deletedIds = memosWithRefs.value
                .filter { it.memo.id in ids && it.memo.deletedAt != null }
                .map { it.memo.id }
                .toSet()
            memosWithRefs.value = memosWithRefs.value.filterNot { it.memo.id in deletedIds }
            deletedIds.forEach(imageFileNamesByMemoId::remove)
            return deletedIds.size
        }

        override suspend fun discardMemo(id: String): Int {
            discardedMemoId = id
            if (discardedCount > 0) {
                imageFileNamesByMemoId.remove(id)
            }
            return discardedCount
        }

        override suspend fun deleteTrashedMemosDeletedAtOrBefore(cutoff: Long) {
            purgeCutoff = cutoff
        }

        override suspend fun getAllActiveMemosWithRefs(): List<MemoWithRefs> =
            memosWithRefs.value.filter { it.memo.deletedAt == null }

    }

    private class FakeMemoImageStore : MemoImageStore {

        val deletedFileNames = mutableListOf<MemoImageFileName>()

        override suspend fun saveImage(
            source: com.lambdarc.litememo.domain.model.value.ImageSourceReference
        ) = error("saveImage is not used by RoomMemoRepositoryTest.")

        override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
            deletedFileNames += fileNames
        }

        override fun resolveImagePath(fileName: MemoImageFileName): String =
            error("resolveImagePath is not used by RoomMemoRepositoryTest.")
    }

}
