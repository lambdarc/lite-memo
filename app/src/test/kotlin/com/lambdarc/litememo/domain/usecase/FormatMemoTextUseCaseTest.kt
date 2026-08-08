package com.lambdarc.litememo.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FormatMemoTextUseCaseTest {

    private val useCase = FormatMemoTextUseCase()

    @Test
    fun invokeReturnsTitleAndBodySeparatedByBlankLine() {
        // Arrange
        val title = "買い物リスト"
        val body = "卵、牛乳、コーヒー豆"

        // Act
        val result = useCase(title, body)

        // Assert
        assertEquals("買い物リスト\n\n卵、牛乳、コーヒー豆", result)
    }

    @Test
    fun invokeReturnsTitleOnlyWhenBodyIsEmpty() {
        // Act
        val result = useCase("買い物リスト", "")

        // Assert
        assertEquals("買い物リスト", result)
    }

    @Test
    fun invokeReturnsTitleOnlyWhenBodyIsBlank() {
        // Act
        val result = useCase("買い物リスト", "   ")

        // Assert
        assertEquals("買い物リスト", result)
    }

    @Test
    fun invokeReturnsBodyOnlyWhenTitleIsEmpty() {
        // Act
        val result = useCase("", "卵、牛乳、コーヒー豆")

        // Assert
        assertEquals("卵、牛乳、コーヒー豆", result)
    }

    @Test
    fun invokeReturnsBodyOnlyWhenTitleIsBlank() {
        // Act
        val result = useCase("   ", "卵、牛乳、コーヒー豆")

        // Assert
        assertEquals("卵、牛乳、コーヒー豆", result)
    }

    @Test
    fun invokeReturnsNullWhenBothAreEmpty() {
        // Act
        val result = useCase("", "")

        // Assert
        assertNull(result)
    }

    @Test
    fun invokeReturnsNullWhenBothAreBlank() {
        // Act
        val result = useCase("   ", "  ")

        // Assert
        assertNull(result)
    }
}
