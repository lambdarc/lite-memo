package com.lambdarc.litememo.data.repository

import com.lambdarc.litememo.data.local.dao.RecordingMemoBulkDao
import com.lambdarc.litememo.data.local.entity.MemoImageEntity
import com.lambdarc.litememo.data.local.entity.MemoTagRefEntity
import com.lambdarc.litememo.data.mapper.toDomain
import com.lambdarc.litememo.data.mapper.toMemoWithRefs
import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.TagId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RoomMemoRepositorySnapshotTest {

    @Test
    fun boundarySnapshotCheckAcceptsGapsInTagPositions() = runTest {
        // Arrange
        val current = memoFixture().toMemoWithRefs().copy(
            tagRefs = listOf(MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 2))
        )
        val dao = RecordingMemoBulkDao(activeMemosById = mapOf("memo-1" to current))
        val repository = RoomMemoRepository(dao, dao, FakeMemoImageStore())

        // Act
        // Boundary: normalized positions preserve the logical order and pass the check.
        repository.saveActiveMemoBulkWrites(
            listOf(ActiveMemoBulkWrite.CheckOnly(expectedMemo = current.toDomain()))
        )

        // Assert
        assertEquals(emptyList<String>(), dao.calls)
    }

    @Test
    fun errorSnapshotCheckRejectsBodyChangeWithSameTimestamp() = runTest {
        // Arrange
        val original = memoFixture().toMemoWithRefs()
        val current = original.copy(memo = original.memo.copy(body = "Concurrent edit"))
        val dao = RecordingMemoBulkDao(activeMemosById = mapOf("memo-1" to current))
        val repository = RoomMemoRepository(dao, dao, FakeMemoImageStore())

        // Act
        // Error: equal timestamps do not make different content safe to overwrite.
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(
                    ActiveMemoBulkWrite.Update(
                        expectedMemo = original.toDomain(),
                        updatedMemo = original.toDomain().copy(isFavorite = true)
                    )
                )
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(emptyList<String>(), dao.calls) }
        )
    }

    @Test
    fun errorSnapshotCheckRejectsTagChangesForCheckOnlyWrite() = runTest {
        // Arrange
        val original = memoFixture().toMemoWithRefs()
        val current = original.copy(
            tagRefs = listOf(MemoTagRefEntity(memoId = "memo-1", tagId = "tag-1", position = 0))
        )
        val dao = RecordingMemoBulkDao(activeMemosById = mapOf("memo-1" to current))
        val repository = RoomMemoRepository(dao, dao, FakeMemoImageStore())

        // Act
        // Error: unchanged members must also retain the observed tag references.
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(ActiveMemoBulkWrite.CheckOnly(expectedMemo = original.toDomain()))
            )
        }.exceptionOrNull()

        // Assert
        assertEquals(IllegalStateException::class.java, error?.javaClass)
    }

    @Test
    fun errorSnapshotCheckRejectsImageChangesWithoutDeletingFiles() = runTest {
        // Arrange
        val original = memoFixture().toMemoWithRefs()
        val current = original.copy(
            imageRefs = listOf(
                MemoImageEntity(
                    id = "image-new",
                    memoId = "memo-1",
                    fileName = "new.jpg",
                    position = 0
                )
            )
        )
        val dao = RecordingMemoBulkDao(activeMemosById = mapOf("memo-1" to current))
        val store = FakeMemoImageStore()
        val repository = RoomMemoRepository(dao, dao, store)

        // Act
        // Error: image references participate in the snapshot check before any cleanup.
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(
                    ActiveMemoBulkWrite.Update(
                        expectedMemo = original.toDomain(),
                        updatedMemo = original.toDomain().copy(isFavorite = true)
                    )
                )
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(emptyList<MemoImageFileName>(), store.deletedFileNames) }
        )
    }

    @Test
    fun errorSnapshotCheckRejectsReorderedTagReferences() = runTest {
        // Arrange
        val original = memoFixture(tagIds = listOf(TagId("tag-1"), TagId("tag-2")))
            .toMemoWithRefs()
        val current = original.copy(
            tagRefs = original.tagRefs.map { it.copy(position = 1 - it.position) }
        )
        val dao = RecordingMemoBulkDao(activeMemosById = mapOf("memo-1" to current))
        val repository = RoomMemoRepository(dao, dao, FakeMemoImageStore())

        // Act
        // Error: normalizing gaps must not hide a reordering of the same tag set.
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(ActiveMemoBulkWrite.CheckOnly(expectedMemo = original.toDomain()))
            )
        }.exceptionOrNull()

        // Assert
        assertEquals(IllegalStateException::class.java, error?.javaClass)
    }

    @Test
    fun errorSnapshotCheckRejectsReorderedImageReferences() = runTest {
        // Arrange
        val original = memoFixture(
            images = listOf(
                memoImageFixture(id = "image-1", fileName = "image-1.jpg"),
                memoImageFixture(id = "image-2", fileName = "image-2.jpg")
            )
        ).toMemoWithRefs()
        val current = original.copy(
            imageRefs = original.imageRefs.map { it.copy(position = 1 - it.position) }
        )
        val dao = RecordingMemoBulkDao(activeMemosById = mapOf("memo-1" to current))
        val store = FakeMemoImageStore()
        val repository = RoomMemoRepository(dao, dao, store)

        // Act
        // Error: image order carries meaning, so a swap is a conflict, not a gap.
        val error = runCatching {
            repository.saveActiveMemoBulkWrites(
                listOf(
                    ActiveMemoBulkWrite.Update(
                        expectedMemo = original.toDomain(),
                        updatedMemo = original.toDomain().copy(isFavorite = true)
                    )
                )
            )
        }.exceptionOrNull()

        // Assert
        assertAll(
            { assertEquals(IllegalStateException::class.java, error?.javaClass) },
            { assertEquals(emptyList<String>(), dao.calls) },
            { assertEquals(emptyList<MemoImageFileName>(), store.deletedFileNames) }
        )
    }

}
