package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DeleteMemoImagesUseCaseTest {

    @Test
    fun interactionInvokeDelegatesFileNamesToStore() = runTest {
        // Arrange
        val store = FakeMemoImageStore()
        val fileNames = listOf(MemoImageFileName("image-1.jpg"), MemoImageFileName("image-2.jpg"))
        val useCase = DeleteMemoImagesUseCase(store)

        // Act
        // Interaction: deletion policy stays behind MemoImageStore.
        useCase(fileNames)

        // Assert
        assertEquals(fileNames, store.deletedFileNames)
    }

    @Test
    fun errorInvokePropagatesStoreFailure() {
        // Arrange
        val store = FakeMemoImageStore()
        store.deleteError = IllegalStateException("delete failed")
        val useCase = DeleteMemoImagesUseCase(store)

        // Act & Assert
        // Error: delete failures are not swallowed by the domain use case.
        assertThrows(IllegalStateException::class.java) {
            runTest {
                useCase(listOf(MemoImageFileName("image-1.jpg")))
            }
        }
    }

}
