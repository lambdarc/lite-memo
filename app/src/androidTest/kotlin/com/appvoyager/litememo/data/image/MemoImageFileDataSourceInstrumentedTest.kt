package com.appvoyager.litememo.data.image

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MemoImageFileDataSourceInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dataSource: MemoImageFileDataSource

    @Before
    fun setUp() {
        dataSource = MemoImageFileDataSource(context, Dispatchers.IO)
        imagesDir().deleteRecursively()
    }

    @After
    fun tearDown() {
        imagesDir().deleteRecursively()
    }

    @Test
    fun normalCopyImageCopiesSourceFileToMemoImagesDirectory() = runTest {
        // Arrange
        val source = sourceFile("source-copy.txt", "image-bytes")

        // Act
        // Normal: selected file content is copied into app-managed storage.
        dataSource.copyImage(Uri.fromFile(source).toString(), "image-1.img")

        // Assert
        assertEquals("image-bytes", File(imagesDir(), "image-1.img").readText())
    }

    @Test
    fun normalDeleteImageDeletesExistingFile() = runTest {
        // Arrange
        val target = File(imagesDir(), "image-1.img").apply {
            parentFile?.mkdirs()
            writeText("image-bytes")
        }

        // Act
        // Normal: stored image files can be removed by file name.
        dataSource.deleteImage("image-1.img")

        // Assert
        assertFalse(target.exists())
    }

    @Test
    fun boundaryDeleteImageIgnoresMissingFile() = runTest {
        // Arrange
        val target = File(imagesDir(), "missing.img")

        // Act
        // Boundary: missing stored files are treated as no-op.
        dataSource.deleteImage("missing.img")

        // Assert
        assertFalse(target.exists())
    }

    @Test
    fun normalImageFilePathReturnsPathInMemoImagesDirectory() {
        // Arrange
        val expected = File(imagesDir(), "image-1.img").absolutePath

        // Act
        // Normal: display paths point at app-managed image storage.
        val path = dataSource.imageFilePath("image-1.img")

        // Assert
        assertEquals(expected, path)
    }

    @Test
    fun boundaryDetectExtensionReturnsNullForFileUriWithoutMimeType() = runTest {
        // Arrange
        val source = sourceFile("source-unknown.bin", "image-bytes")

        // Act
        // Boundary: file URIs without MIME metadata use caller fallback.
        val extension = dataSource.detectExtension(Uri.fromFile(source).toString())

        // Assert
        assertNull(extension)
    }

    @Test
    fun errorCopyImageDeletesPartialFileWhenInputStreamCannotOpen() = runTest {
        // Arrange
        val missing = File(context.cacheDir, "missing-source-file")

        // Act
        // Error: failed copies do not leave a target file behind.
        runCatching {
            dataSource.copyImage(Uri.fromFile(missing).toString(), "partial.img")
        }

        // Assert
        assertFalse(File(imagesDir(), "partial.img").exists())
    }

    @Test
    fun normalImagesDirIsCreatedWhenCopying() = runTest {
        // Arrange
        val source = sourceFile("source-dir.txt", "image-bytes")

        // Act
        // Normal: copy creates the memo image directory lazily.
        dataSource.copyImage(Uri.fromFile(source).toString(), "image-1.img")

        // Assert
        assertTrue(imagesDir().isDirectory)
    }

    private fun sourceFile(name: String, content: String): File =
        File(context.cacheDir, name).apply {
            parentFile?.mkdirs()
            writeText(content)
        }

    private fun imagesDir(): File = File(context.filesDir, MemoImageFileDataSource.IMAGES_DIR)
}
