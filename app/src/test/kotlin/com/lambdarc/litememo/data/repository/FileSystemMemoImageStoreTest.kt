package com.lambdarc.litememo.data.repository

import com.lambdarc.litememo.data.export.MemoArchiveLimits
import com.lambdarc.litememo.data.image.MemoImageFileDataSource
import com.lambdarc.litememo.domain.exception.MemoImageTooLargeException
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.provider.MemoImageIdProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.IOException

class FileSystemMemoImageStoreTest {

    @Test
    fun normalSaveImageBuildsFileNameFromIdAndDetectedExtension() = runTest {
        // Arrange
        val dataSource = mockk<MemoImageFileDataSource>()
        coEvery { dataSource.detectExtension("content://memo/image") } returns "jpg"
        coEvery {
            dataSource.copyImage("content://memo/image", "image-1.jpg", DEFAULT_IMAGE_LIMIT_BYTES)
        } returns Unit
        val store = store(dataSource)

        // Act
        // Normal: stored image metadata uses generated id and detected extension.
        val image = store.saveImage(ImageSourceReference("content://memo/image"))

        // Assert
        assertEquals(
            MemoImage(MemoImageId("image-1"), MemoImageFileName("image-1.jpg")),
            image
        )
    }

    @Test
    fun boundarySaveImageFallsBackToImgExtensionWhenMimeUnknown() = runTest {
        // Arrange
        val dataSource = mockk<MemoImageFileDataSource>()
        coEvery { dataSource.detectExtension("content://memo/image") } returns null
        coEvery {
            dataSource.copyImage("content://memo/image", "image-1.img", DEFAULT_IMAGE_LIMIT_BYTES)
        } returns Unit
        val store = store(dataSource)

        // Act
        // Boundary: unknown MIME type still creates a stable file name.
        val image = store.saveImage(ImageSourceReference("content://memo/image"))

        // Assert
        assertEquals(MemoImageFileName("image-1.img"), image.fileName)
    }

    @Test
    fun errorSaveImagePropagatesCopyFailure() {
        // Arrange
        val dataSource = mockk<MemoImageFileDataSource>()
        coEvery { dataSource.detectExtension("content://memo/image") } returns "jpg"
        coEvery {
            dataSource.copyImage("content://memo/image", "image-1.jpg", DEFAULT_IMAGE_LIMIT_BYTES)
        } throws IOException("copy failed")
        val store = store(dataSource)

        // Act & Assert
        // Error: copy failures are exposed to callers.
        assertThrows(IOException::class.java) {
            runTest {
                store.saveImage(ImageSourceReference("content://memo/image"))
            }
        }
    }

    @Test
    fun interactionDeleteImagesDelegatesEachFileName() = runTest {
        // Arrange
        val dataSource = mockk<MemoImageFileDataSource>()
        coEvery { dataSource.deleteImage(any()) } returns true
        val store = store(dataSource)

        // Act
        // Interaction: deletion keeps file-system details in the data source.
        store.deleteImages(
            listOf(MemoImageFileName("image-1.jpg"), MemoImageFileName("image-2.png"))
        )

        // Assert
        coVerify(exactly = 1) { dataSource.deleteImage("image-1.jpg") }
        coVerify(exactly = 1) { dataSource.deleteImage("image-2.png") }
    }

    @Test
    fun normalResolveImagePathDelegatesToDataSource() {
        // Arrange
        val dataSource = mockk<MemoImageFileDataSource>()
        every { dataSource.imageFilePath("image-1.jpg") } returns "/files/memo_images/image-1.jpg"
        val store = store(dataSource)

        // Act
        // Normal: path resolution stays behind the store abstraction.
        val path = store.resolveImagePath(MemoImageFileName("image-1.jpg"))

        // Assert
        assertEquals("/files/memo_images/image-1.jpg", path)
    }

    @Test
    fun errorSaveImagePropagatesTooLargeImage() {
        // Arrange
        val dataSource = mockk<MemoImageFileDataSource>()
        coEvery { dataSource.detectExtension("content://memo/image") } returns "jpg"
        coEvery {
            dataSource.copyImage("content://memo/image", "image-1.jpg", IMAGE_LIMIT_BYTES)
        } throws MemoImageTooLargeException(IMAGE_LIMIT_BYTES)
        val store = store(
            dataSource,
            MemoArchiveLimits.DEFAULT.copy(maxImageBytes = IMAGE_LIMIT_BYTES)
        )

        // Act & Assert
        // Error: the copy is bounded by the per-image limit of the backup archive.
        assertThrows(MemoImageTooLargeException::class.java) {
            runTest {
                store.saveImage(ImageSourceReference("content://memo/image"))
            }
        }
    }

    private fun store(
        dataSource: MemoImageFileDataSource,
        archiveLimits: MemoArchiveLimits = MemoArchiveLimits.DEFAULT
    ) = FileSystemMemoImageStore(dataSource, FixedMemoImageIdProvider("image-1"), archiveLimits)

    private class FixedMemoImageIdProvider(private val id: String) : MemoImageIdProvider {
        override fun newMemoImageId(): MemoImageId = MemoImageId(id)
    }

    private companion object {
        const val IMAGE_LIMIT_BYTES = 1_024L
        val DEFAULT_IMAGE_LIMIT_BYTES = MemoArchiveLimits.DEFAULT.maxImageBytes
    }
}
