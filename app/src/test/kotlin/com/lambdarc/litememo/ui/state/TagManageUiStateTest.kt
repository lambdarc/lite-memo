package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.TagUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TagManageUiStateTest {

    @Test
    fun normalContentKeepsTagsAndOverlays() {
        // Arrange
        val tag = tagUiModel()

        // Act
        // Normal: content status keeps its tags and overlays.
        val state = TagManageUiState(
            status = ScreenUiStatus.CONTENT,
            tags = listOf(tag),
            editingTag = TagEditUiState(name = "Draft"),
            showDeleteDialog = tag
        )

        // Assert
        assertEquals(listOf(tag), state.tags)
    }

    @Test
    fun errorConstructorThrowsWhenTagsRemainOutsideContent() {
        // Act & Assert
        // Error: tags cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            TagManageUiState(status = ScreenUiStatus.ERROR, tags = listOf(tagUiModel()))
        }
    }

    @Test
    fun errorConstructorThrowsWhenEditingTagRemainsOutsideContent() {
        // Act & Assert
        // Error: the edit dialog cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            TagManageUiState(status = ScreenUiStatus.ERROR, editingTag = TagEditUiState())
        }
    }

    @Test
    fun errorConstructorThrowsWhenDeleteDialogRemainsOutsideContent() {
        // Act & Assert
        // Error: the delete dialog cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            TagManageUiState(status = ScreenUiStatus.LOADING, showDeleteDialog = tagUiModel())
        }
    }

    private fun tagUiModel() = TagUiModel(
        id = TagId("tag-1"),
        name = "Work",
        colorArgb = 0xFF000000
    )

}
