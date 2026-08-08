package com.lambdarc.litememo.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lambdarc.litememo.data.export.MemoArchiveLimits
import com.lambdarc.litememo.data.export.MemoArchiveReader
import com.lambdarc.litememo.data.export.MemoExportSessionDataSource
import com.lambdarc.litememo.data.image.MemoImageFileDataSource
import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ExportFileReference
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FileSystemMemoExportArchiveRepositoryInstrumentedTest {

    private lateinit var context: Context
    private lateinit var imageDir: File
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        imageDir = File(context.filesDir, MemoImageFileDataSource.IMAGES_DIR).apply { mkdirs() }
        File(context.cacheDir, "prepared_exports").deleteRecursively()
    }

    @After
    fun tearDown() {
        imageDir.deleteRecursively()
        File(context.cacheDir, "prepared_exports").deleteRecursively()
    }

    @Test
    fun normalPrepareAndWriteRoundTripsImageOnlyMemo() = runTest {
        // Arrange
        val imageBytes = ByteArray(4_096) { index -> (index % 251).toByte() }
        File(imageDir, "picked.jpg").writeBytes(imageBytes)
        val repository = repository()
        val destination = Uri.parse("content://$AUTHORITY/export-${System.nanoTime()}.zip")

        // Act
        // Normal: a verified private ZIP restores the image-only memo and its bytes.
        val token = repository.prepare(exportData())
        repository.write(token, ExportFileReference(destination.toString()))
        val archiveBytes = context.contentResolver.openInputStream(destination)!!.use { input ->
            input.readBytes()
        }
        val restoredImages = linkedMapOf<String, ByteArrayOutputStream>()
        val manifest = ByteArrayInputStream(archiveBytes).use { input ->
            MemoArchiveReader(json, MemoArchiveLimits.DEFAULT).read(input) { metadata ->
                ByteArrayOutputStream().also { restoredImages[metadata.id] = it }
            }
        }
        repository.discard(token)

        // Assert
        assertEquals(listOf("image-1"), manifest.memos.single().images.map { it.id })
        assertEquals(
            imageBytes.toList(),
            restoredImages.getValue("image-1").toByteArray().toList()
        )
    }

    @Test
    fun normalWriteTruncatesDestination() = runTest {
        // Arrange
        val imageBytes = ByteArray(4_096) { index -> (index % 251).toByte() }
        File(imageDir, "picked.jpg").writeBytes(imageBytes)
        val repository = repository()
        val token = repository.prepare(exportData())
        val destination = Uri.parse("content://$AUTHORITY/export-${System.nanoTime()}.zip")
        context.contentResolver.openOutputStream(destination, "w")!!.use { output ->
            output.write(ByteArray(64_000) { 1 })
        }

        // Act
        // Normal: writing the prepared archive truncates preexisting destination bytes.
        repository.write(token, ExportFileReference(destination.toString()))
        val archiveSize = context.contentResolver.openInputStream(destination)!!.use { input ->
            input.readBytes().size
        }
        repository.discard(token)

        // Assert
        assertTrue(archiveSize < 64_000)
    }

    @Test
    fun errorMissingImagePreventsPreparedArchiveAndCleansPartialFile() = runTest {
        // Arrange
        val repository = repository()

        // Act
        // Error: a missing image fails before any picker can be requested.
        val failure = runCatching { repository.prepare(exportData()) }.exceptionOrNull()

        // Assert
        assertEquals(IOException::class.java, failure?.javaClass)
        assertTrue(File(context.cacheDir, "prepared_exports").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun normalStartupCleanupDeletesAbandonedPreparedArchive() = runTest {
        // Arrange
        val staleArchive = File(context.cacheDir, "prepared_exports/stale-token.zip")
        staleArchive.parentFile?.mkdirs()
        staleArchive.writeBytes(byteArrayOf(1, 2, 3))

        // Act
        // Normal: a process-death leftover is reclaimed on the next app start.
        repository().deleteAbandonedPreparedExports()

        // Assert
        assertFalse(staleArchive.exists())
    }

    private fun repository() = FileSystemMemoExportArchiveRepository(
        context = context,
        json = json,
        limits = MemoArchiveLimits.DEFAULT,
        sessionDataSource = MemoExportSessionDataSource(context, dispatcher),
        imageFileDataSource = MemoImageFileDataSource(context, dispatcher),
        ioDispatcher = dispatcher
    )

    private fun exportData() = ExportData(
        version = 1,
        exportedAt = TimestampMillis(2_000L),
        tags = emptyList(),
        memos = listOf(
            Memo(
                id = MemoId("memo-1"),
                title = MemoTitle(""),
                body = MemoBody(""),
                createdAt = TimestampMillis(1_000L),
                updatedAt = TimestampMillis(2_000L),
                isFavorite = true,
                tagIds = emptyList(),
                deletedAt = null,
                images = listOf(
                    MemoImage(MemoImageId("image-1"), MemoImageFileName("picked.jpg"))
                )
            )
        )
    )

    private companion object {
        const val AUTHORITY = "com.lambdarc.litememo.test.nontruncating"
    }

}
