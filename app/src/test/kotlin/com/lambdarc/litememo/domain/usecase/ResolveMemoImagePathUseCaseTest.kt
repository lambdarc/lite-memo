package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveMemoImagePathUseCaseTest {

    @Test
    fun normalInvokeReturnsResolvedPath() {
        // Arrange
        val useCase = ResolveMemoImagePathUseCase(FakeMemoImageStore())

        // Act
        // Normal: the store resolves display paths for saved image files.
        val path = useCase(MemoImageFileName("image-1.jpg"))

        // Assert
        assertEquals("/images/image-1.jpg", path)
    }

}
