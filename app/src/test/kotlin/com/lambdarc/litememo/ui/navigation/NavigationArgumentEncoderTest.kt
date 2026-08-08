package com.lambdarc.litememo.ui.navigation

import com.lambdarc.litememo.domain.model.value.MemoId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavigationArgumentEncoderTest {

    @Test
    fun boundaryMemoEditRouteEncodesSpecialCharactersInMemoId() {
        // Arrange
        val memoId = MemoId("memo/1 ?")

        // Act
        // Boundary: the typed identifier is serialized only at the navigation route boundary
        val route = memoEditRouteWithId(memoId)

        // Assert
        assertEquals("memo_edit?memoId=memo%2F1%20%3F", route)
    }
}
