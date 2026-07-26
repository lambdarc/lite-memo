package com.appvoyager.litememo.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appvoyager.litememo.data.export.MemoArchiveLimits
import com.appvoyager.litememo.data.export.MemoArchiveReader
import com.appvoyager.litememo.data.export.MemoExportSessionDataSource
import com.appvoyager.litememo.data.image.MemoImageFileDataSource
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoImage
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoBody
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.MemoImageFileName
import com.appvoyager.litememo.domain.model.value.MemoImageId
import com.appvoyager.litememo.domain.model.value.MemoTitle
import com.appvoyager.litememo.domain.model.value.TimestampMillis
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
    fun normalPrepareAndWriteRoundTripsImageOnlyMemoAndTruncatesDestination() = runTest {
        // Arrange
        val imageBytes = ByteArray(4_096) { index -> (index % 251).toByte() }
        File(imageDir, "picked.jpg").writeBytes(imageBytes)
        val repository = repository()
        val destination = Uri.parse("content://$AUTHORITY/export-${System.nanoTime()}.zip")
        context.contentResolver.openOutputStream(destination, "w")!!.use { output ->
            output.write(ByteArray(64_000) { 1 })
        }

        // Act
        // Normal: a verified private ZIP is copied with truncating semantics.
        val token = repository.prepare(exportData())
        repository.write(token, ExportFileReference(destination.toString()))
        val archiveBytes = context.contentResolver.openInputStream(destination)!!.use { input ->
            input.readBytes()
        }
        assertTrue(archiveBytes.size < 64_000)
        val restoredImages = linkedMapOf<String, ByteArrayOutputStream>()
        val manifest = ByteArrayInputStream(archiveBytes).use { input ->
            MemoArchiveReader(json, MemoArchiveLimits.DEFAULT).read(input) { metadata ->
                ByteArrayOutputStream().also { restoredImages[metadata.id] = it }
            }
        }
        repository.discard(token)

        // Assert
        assertEquals(
            listOf("image-1") to imageBytes.toList(),
            manifest.memos.single().images.map { it.id } to
                restoredImages.getValue("image-1").toByteArray().toList()
        )
    }

    @Test
    fun errorMissingImagePreventsPreparedArchiveAndCleansPartialFile() = runTest {
        // Arrange
        val repository = repository()

        // Act
        // Error: a missing image fails before any picker can be requested.
        val failure = runCatching { repository.prepare(exportData()) }.exceptionOrNull()

        // Assert
        assertEquals(
            IOException::class.java to false,
            failure?.javaClass to
                File(context.cacheDir, "prepared_exports").listFiles().orEmpty().isNotEmpty()
        )
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
        const val AUTHORITY = "com.appvoyager.litememo.test.nontruncating"
    }

}
