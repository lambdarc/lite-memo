package com.lambdarc.litememo.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.data.local.LiteMemoDatabase
import com.lambdarc.litememo.data.local.entity.TagEntity
import com.lambdarc.litememo.domain.exception.MemoEditConflictException
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.MemoImageStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMemoRepositoryInstrumentedTest {

    private lateinit var database: LiteMemoDatabase
    private lateinit var imageStore: RecordingMemoImageStore
    private lateinit var repository: RoomMemoRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LiteMemoDatabase::class.java).build()
        imageStore = RecordingMemoImageStore()
        repository = RoomMemoRepository(
            memoDao = database.memoDao(),
            memoBulkDao = database.memoBulkDao(),
            memoTrashDao = database.memoTrashDao(),
            memoImageStore = imageStore
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun normalSavedMemoIsAcceptedAsExpectedSnapshotForNextSave() = runTest {
        // Arrange
        database.tagDao().insertOrUpdateAllTags(listOf(tagEntity("tag-b"), tagEntity("tag-a")))
        val first = memo(tagIds = listOf(TagId("tag-b"), TagId("tag-a")), fileName = "first.jpg")
        repository.saveMemo(first, expectedMemo = null)
        val second = first.copy(title = MemoTitle("Second"), updatedAt = TimestampMillis(3_000L))

        // Act
        // Normal: the memo written by a save round-trips through Room as the next baseline
        repository.saveMemo(second, expectedMemo = first)

        // Assert
        assertEquals(second, repository.getActiveMemo(first.id))
    }

    @Test
    fun errorSaveBasedOnSnapshotBeforeImportKeepsImportedImages() = runTest {
        // Arrange
        val original = memo(fileName = "before-import.jpg")
        repository.saveMemo(original, expectedMemo = null)
        RoomMemoImportRepository(
            memoDao = database.memoDao(),
            tagDao = database.tagDao(),
            database = database,
            memoImageStore = imageStore
        ).import(exportData(original.copy(images = listOf(image("after-import.jpg")))))
        imageStore.deletedFileNames.clear()
        val staleEdit = original.copy(title = MemoTitle("Edited"))

        // Act
        // Error: an edit based on the pre-import snapshot does not write back old file names
        val failure = runCatching { repository.saveMemo(staleEdit, expectedMemo = original) }
            .exceptionOrNull()

        // Assert
        assertEquals(MemoEditConflictException::class.java, failure?.javaClass)
        assertEquals(
            listOf("after-import.jpg"),
            repository.getActiveMemo(original.id)?.images?.map { it.fileName.value }
        )
        assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames)
    }

    @Test
    fun normalSavedMemoIsAcceptedAsExpectedSnapshotForTrashMove() = runTest {
        // Arrange
        database.tagDao().insertOrUpdateAllTags(listOf(tagEntity("tag-b"), tagEntity("tag-a")))
        val saved = memo(tagIds = listOf(TagId("tag-b"), TagId("tag-a")), fileName = "saved.jpg")
        repository.saveMemo(saved, expectedMemo = null)

        // Act
        // Normal: the memo written by a save round-trips through Room as the trash baseline
        repository.moveMemoToTrashIfUnchanged(saved, TimestampMillis(3_000L))

        // Assert
        assertEquals(null, repository.getActiveMemo(saved.id))
    }

    @Test
    fun errorTrashMoveBasedOnSnapshotBeforeImportKeepsImportedMemo() = runTest {
        // Arrange
        val original = memo(fileName = "before-import.jpg")
        repository.saveMemo(original, expectedMemo = null)
        RoomMemoImportRepository(
            memoDao = database.memoDao(),
            tagDao = database.tagDao(),
            database = database,
            memoImageStore = imageStore
        ).import(exportData(original.copy(images = listOf(image("after-import.jpg")))))

        // Act
        // Error: a trash move based on the pre-import snapshot leaves the imported memo active
        val failure = runCatching {
            repository.moveMemoToTrashIfUnchanged(original, TimestampMillis(3_000L))
        }.exceptionOrNull()

        // Assert
        assertEquals(MemoEditConflictException::class.java, failure?.javaClass)
        assertEquals(
            listOf("after-import.jpg"),
            repository.getActiveMemo(original.id)?.images?.map { it.fileName.value }
        )
    }

    private fun memo(tagIds: List<TagId> = emptyList(), fileName: String) = Memo(
        id = MemoId("memo"),
        title = MemoTitle("Title"),
        body = MemoBody("Body"),
        createdAt = TimestampMillis(1_000L),
        updatedAt = TimestampMillis(2_000L),
        tagIds = tagIds,
        images = listOf(image(fileName))
    )

    private fun image(fileName: String) = MemoImage(
        id = MemoImageId("image"),
        fileName = MemoImageFileName(fileName)
    )

    private fun tagEntity(id: String) = TagEntity(
        id = id,
        name = id,
        colorArgb = 0xFF6750A4,
        createdAt = 1_000L
    )

    private fun exportData(memo: Memo) = ExportData(
        version = ExportData.CURRENT_VERSION,
        exportedAt = TimestampMillis(2_000L),
        tags = emptyList(),
        memos = listOf(memo)
    )

    private class RecordingMemoImageStore : MemoImageStore {

        val deletedFileNames = mutableListOf<MemoImageFileName>()

        override suspend fun saveImage(source: ImageSourceReference): MemoImage =
            error("saveImage is not used by RoomMemoRepositoryInstrumentedTest.")

        override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
            deletedFileNames += fileNames
        }

        override fun resolveImagePath(fileName: MemoImageFileName): String =
            error("resolveImagePath is not used by RoomMemoRepositoryInstrumentedTest.")
    }

}
