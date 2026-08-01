package com.appvoyager.litememo.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.appvoyager.litememo.data.local.LiteMemoDatabase
import com.appvoyager.litememo.data.local.dao.MemoDao
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.TagEntity
import com.appvoyager.litememo.domain.exception.ImportTagNameConflictException
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoImage
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.ImageSourceReference
import com.appvoyager.litememo.domain.model.value.MemoBody
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.MemoImageFileName
import com.appvoyager.litememo.domain.model.value.MemoTitle
import com.appvoyager.litememo.domain.model.value.TagColor
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.model.value.TimestampMillis
import com.appvoyager.litememo.domain.repository.MemoImageStore
import com.appvoyager.litememo.domain.usecase.ExportMemosUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMemoImportRepositoryInstrumentedTest {

    private lateinit var database: LiteMemoDatabase
    private lateinit var memoDao: MemoDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LiteMemoDatabase::class.java).build()
        memoDao = database.memoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun errorMemoWriteFailureRollsBackTagWrite() = runTest {
        // Arrange
        val repository = repository(RecordingMemoImageStore())
        val data = exportData(
            tags = listOf(tag(id = "new-tag")),
            memos = listOf(memo(id = "memo", tagIds = listOf(TagId("missing-tag"))))
        )

        // Act
        // Error: a ref constraint failure after tag upsert aborts the whole import transaction.
        val failure = runCatching { repository.import(data) }.exceptionOrNull()
        val persistedTag = database.tagDao().getTag("new-tag")

        // Assert
        assertEquals(SQLiteConstraintException::class.java, failure?.javaClass)
        assertEquals(null, persistedTag)
    }

    @Test
    fun normalImportPersistsTagMemoAndRefsAtomically() = runTest {
        // Arrange
        val repository = repository(RecordingMemoImageStore())
        val data = exportData(
            tags = listOf(tag(id = "tag")),
            memos = listOf(memo(id = "memo", tagIds = listOf(TagId("tag"))))
        )

        // Act
        // Normal: a successful transaction exposes all imported aggregates together.
        repository.import(data)
        val persistedTag = database.tagDao().getTag("tag")
        val persistedMemo = memoDao.getActiveMemoWithRefs("memo")

        // Assert
        assertEquals("tag", persistedTag?.id)
        assertEquals("memo", persistedMemo?.memo?.id)
        assertEquals(listOf("tag"), persistedMemo?.tagRefs?.map { it.tagId }.orEmpty())
    }

    @Test
    fun interactionRollbackSkipsImageCleanup() = runTest {
        // Arrange
        seedMemoWithImage()
        val imageStore = RecordingMemoImageStore()
        val repository = repository(imageStore)
        val data = exportData(
            memos = listOf(memo(id = "memo", tagIds = listOf(TagId("missing-tag"))))
        )

        // Act
        // Interaction/Error: cleanup is unreachable when the DB transaction rolls back.
        val failure = runCatching { repository.import(data) }.exceptionOrNull()
        val persistedMemo = memoDao.getActiveMemoWithRefs("memo")

        // Assert
        assertEquals(SQLiteConstraintException::class.java, failure?.javaClass)
        assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames)
        assertEquals(
            listOf("old.jpg"),
            persistedMemo?.imageRefs?.map { it.fileName }.orEmpty()
        )
    }

    @Test
    fun interactionSuccessfulImportCleansImagesOnlyAfterCommit() = runTest {
        // Arrange
        seedMemoWithImage()
        val imageStore = CommitObservingMemoImageStore(database, memoDao)
        val repository = repository(imageStore)

        // Act
        // Interaction: obsolete image cleanup observes committed DB state outside a transaction.
        repository.import(exportData(memos = listOf(memo(id = "memo"))))

        // Assert
        assertEquals(listOf(MemoImageFileName("old.jpg")), imageStore.deletedFileNames)
        assertEquals(false, imageStore.databaseInTransactionAtCleanup)
        assertEquals(emptyList<String>(), imageStore.persistedImageFileNamesAtCleanup)
    }

    @Test
    fun normalImportOverwritesExistingMemoWithSameId() = runTest {
        // Arrange
        seedMemoWithImage()
        val repository = repository(RecordingMemoImageStore())

        // Act
        // Normal: importing an existing memo id replaces the stored memo content.
        repository.import(exportData(memos = listOf(memo(id = "memo"))))
        val persistedMemo = memoDao.getActiveMemoWithRefs("memo")

        // Assert
        assertEquals("Title", persistedMemo?.memo?.title)
        assertEquals(2_000L, persistedMemo?.memo?.updatedAt)
    }

    @Test
    fun errorImportRollsBackWhenExistingTagNameConflicts() = runTest {
        // Arrange
        database.tagDao().insertOrUpdateAllTags(
            listOf(tagEntity(id = "existing-tag", name = "Work"))
        )
        val repository = repository(RecordingMemoImageStore())
        val data = exportData(
            tags = listOf(tag(id = "imported-tag", name = "Work")),
            memos = listOf(memo(id = "memo"))
        )

        // Act
        // Error: a name held by a tag the import does not overwrite aborts the whole import.
        val failure = runCatching { repository.import(data) }.exceptionOrNull()
        val persistedMemo = memoDao.getActiveMemoWithRefs("memo")

        // Assert
        assertEquals(
            listOf(TagName("Work")),
            (failure as? ImportTagNameConflictException)?.tagNames
        )
        assertEquals(null, persistedMemo)
    }

    @Test
    fun errorImportReportsEveryConflictingExistingTagName() = runTest {
        // Arrange
        database.tagDao().insertOrUpdateAllTags(
            listOf(
                tagEntity(id = "existing-1", name = "Work"),
                tagEntity(id = "existing-2", name = "Home")
            )
        )
        val repository = repository(RecordingMemoImageStore())
        val data = exportData(
            tags = listOf(
                tag(id = "imported-1", name = "Work"),
                tag(id = "imported-2", name = "Home")
            )
        )

        // Act
        // Error: every conflicting name is collected before any upsert starts.
        val failure = runCatching { repository.import(data) }.exceptionOrNull()

        // Assert
        assertEquals(
            listOf(TagName("Home"), TagName("Work")),
            (failure as? ImportTagNameConflictException)?.tagNames
        )
    }

    @Test
    fun normalImportOverwritesExistingTagHoldingTheSameName() = runTest {
        // Arrange
        database.tagDao().insertOrUpdateAllTags(listOf(tagEntity(id = "tag", name = "Work")))
        val repository = repository(RecordingMemoImageStore())

        // Act
        // Normal: the same tag id keeps its name, so no conflict is reported.
        repository.import(exportData(tags = listOf(tag(id = "tag", name = "Work"))))
        val persistedTag = database.tagDao().findTagByName("Work")

        // Assert
        assertEquals("tag", persistedTag?.id)
    }

    @Test
    fun normalImportKeepsExistingTagsThatAreNotInImportData() = runTest {
        // Arrange
        database.tagDao().insertOrUpdateAllTags(listOf(tagEntity(id = "kept-tag", name = "Kept")))
        val repository = repository(RecordingMemoImageStore())

        // Act
        // Normal: import merges instead of replacing, so untouched tags survive.
        repository.import(exportData(tags = listOf(tag(id = "new-tag", name = "New"))))
        val persistedTagIds = database.tagDao().getAllTags().map { it.id }

        // Assert
        assertEquals(listOf("kept-tag", "new-tag"), persistedTagIds.sorted())
    }

    private fun tagEntity(id: String, name: String) = TagEntity(
        id = id,
        name = name,
        colorArgb = 0xFF6750A4,
        createdAt = 1_000L
    )

    private fun repository(imageStore: MemoImageStore) = RoomMemoImportRepository(
        memoDao = memoDao,
        tagDao = database.tagDao(),
        database = database,
        memoImageStore = imageStore
    )

    private suspend fun seedMemoWithImage() {
        memoDao.upsertMemo(
            MemoEntity(
                id = "memo",
                title = "Old",
                body = "Body",
                createdAt = 1_000L,
                updatedAt = 1_000L,
                isFavorite = false,
                deletedAt = null
            )
        )
        memoDao.insertImageRefs(
            listOf(
                MemoImageEntity(
                    id = "old-image",
                    memoId = "memo",
                    fileName = "old.jpg",
                    position = 0
                )
            )
        )
    }

    private fun exportData(tags: List<Tag> = emptyList(), memos: List<Memo> = emptyList()) =
        ExportData(
            version = ExportMemosUseCase.CURRENT_VERSION,
            exportedAt = TimestampMillis(2_000L),
            tags = tags,
            memos = memos
        )

    private fun tag(id: String, name: String = "Tag") = Tag(
        id = TagId(id),
        name = TagName(name),
        color = TagColor(0xFF6750A4),
        createdAt = TimestampMillis(1_000L)
    )

    private fun memo(id: String, tagIds: List<TagId> = emptyList()) = Memo(
        id = MemoId(id),
        title = MemoTitle("Title"),
        body = MemoBody("Body"),
        createdAt = TimestampMillis(1_000L),
        updatedAt = TimestampMillis(2_000L),
        tagIds = tagIds
    )

    private open class RecordingMemoImageStore : MemoImageStore {

        val deletedFileNames = mutableListOf<MemoImageFileName>()

        override suspend fun saveImage(source: ImageSourceReference): MemoImage =
            error("saveImage is not used by RoomMemoImportRepositoryInstrumentedTest.")

        override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
            deletedFileNames += fileNames
        }

        override fun resolveImagePath(fileName: MemoImageFileName): String =
            error("resolveImagePath is not used by RoomMemoImportRepositoryInstrumentedTest.")
    }

    private class CommitObservingMemoImageStore(
        private val database: LiteMemoDatabase,
        private val memoDao: MemoDao
    ) : RecordingMemoImageStore() {

        var databaseInTransactionAtCleanup: Boolean? = null
            private set
        var persistedImageFileNamesAtCleanup: List<String>? = null
            private set

        override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
            super.deleteImages(fileNames)
            databaseInTransactionAtCleanup = database.inTransaction()
            persistedImageFileNamesAtCleanup = memoDao.getActiveMemoWithRefs("memo")
                ?.imageRefs
                ?.map { it.fileName }
                .orEmpty()
        }
    }

}
