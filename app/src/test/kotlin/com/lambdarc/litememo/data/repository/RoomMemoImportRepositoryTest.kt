package com.lambdarc.litememo.data.repository

import androidx.room.withTransaction
import com.lambdarc.litememo.data.local.LiteMemoDatabase
import com.lambdarc.litememo.data.local.dao.MemoDao
import com.lambdarc.litememo.data.local.dao.TagDao
import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.tagFixture
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RoomMemoImportRepositoryTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(ROOM_DATABASE_EXTENSIONS)
    }

    @Test
    fun errorDuplicateMemoIdsAreRejectedBeforeTransaction() {
        // Arrange
        val repository = repository()
        val data = exportData(
            memos = listOf(memoFixture(id = "memo"), memoFixture(id = "memo"))
        )

        // Act & Assert
        // Error: duplicate memo ids remain invalid at the persistence boundary.
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.import(data) }
        }
    }

    @Test
    fun errorDuplicateTagIdsAreRejectedBeforeTransaction() {
        // Arrange
        val repository = repository()
        val data = exportData(
            tags = listOf(tagFixture(id = "tag"), tagFixture(id = "tag"))
        )

        // Act & Assert
        // Error: duplicate tag ids remain invalid at the persistence boundary.
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.import(data) }
        }
    }

    @Test
    fun coroutineImportDeletesReplacedImageFilesWhenCancelledAfterTransaction() = runTest {
        // Arrange
        val memoDao = mockk<MemoDao>()
        coEvery {
            memoDao.upsertAllMemosWithRefsAndCollectRemovedFileNames(any(), any(), any())
        } returns listOf("old.jpg")
        val database = mockk<LiteMemoDatabase>()
        mockkStatic(ROOM_DATABASE_EXTENSIONS)
        coEvery { database.withTransaction(any<suspend () -> List<String>>()) } coAnswers {
            val removedFileNames = secondArg<suspend () -> List<String>>().invoke()
            currentCoroutineContext().cancel()
            removedFileNames
        }
        val deletedFileNames = mutableListOf<MemoImageFileName>()
        val memoImageStore = mockk<MemoImageStore>()
        coEvery { memoImageStore.deleteImages(any()) } coAnswers {
            currentCoroutineContext().ensureActive()
            deletedFileNames += firstArg<List<MemoImageFileName>>()
        }
        val repository = RoomMemoImportRepository(
            memoDao = memoDao,
            tagDao = mockk<TagDao>(relaxed = true),
            database = database,
            memoImageStore = memoImageStore
        )

        // Act
        // Coroutine: cancellation after the commit does not leave replaced files behind
        launch { repository.import(exportData(memos = listOf(memoFixture(id = "memo")))) }.join()

        // Assert
        assertEquals(listOf(MemoImageFileName("old.jpg")), deletedFileNames)
    }

    private fun repository() = RoomMemoImportRepository(
        memoDao = mockk<MemoDao>(relaxed = true),
        tagDao = mockk<TagDao>(relaxed = true),
        database = mockk<LiteMemoDatabase>(relaxed = true),
        memoImageStore = FakeMemoImageStore()
    )

    private fun exportData(tags: List<Tag> = emptyList(), memos: List<Memo> = emptyList()) =
        ExportData(
            version = ExportData.CURRENT_VERSION,
            exportedAt = TimestampMillis(1_000L),
            tags = tags,
            memos = memos
        )

    private companion object {
        const val ROOM_DATABASE_EXTENSIONS = "androidx.room.RoomDatabaseKt"
    }

}
