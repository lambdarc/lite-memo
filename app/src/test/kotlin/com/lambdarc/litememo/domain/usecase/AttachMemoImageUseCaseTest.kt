package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AttachMemoImageUseCaseTest {

    @Test
    fun normalInvokeReturnsSavedImageFromStore() = runTest {
        // Arrange
        val store = FakeMemoImageStore()
        val useCase = AttachMemoImageUseCase(store)

        // Act
        // Normal: the saved image metadata is returned to the caller.
        val image = useCase(ImageSourceReference("content://memo/image.jpg"))

        // Assert
        assertEquals(memoImageFixture(id = "image-1", fileName = "image-1.jpg"), image)
    }

    @Test
    fun interactionInvokeDelegatesSourceToStore() = runTest {
        // Arrange
        val store = FakeMemoImageStore()
        val source = ImageSourceReference("content://memo/image.jpg")
        val useCase = AttachMemoImageUseCase(store)

        // Act
        // Interaction: the use case keeps source handling inside MemoImageStore.
        useCase(source)

        // Assert
        assertEquals(listOf(source), store.savedSources)
    }

    @Test
    fun errorInvokePropagatesStoreFailure() {
        // Arrange
        val store = FakeMemoImageStore()
        store.saveError = IllegalStateException("copy failed")
        val useCase = AttachMemoImageUseCase(store)

        // Act & Assert
        // Error: copy failures are not swallowed by the domain use case.
        assertThrows(IllegalStateException::class.java) {
            runTest {
                useCase(ImageSourceReference("content://memo/image.jpg"))
            }
        }
    }

}
