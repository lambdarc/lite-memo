package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoUiModel
import com.lambdarc.litememo.ui.model.TagUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class HomeUiStateTest {

    @Test
    fun normalContentKeepsContentOnlyData() {
        // Arrange
        val memos = listOf(memoUiModel())
        val tags = listOf(tagUiModel())

        // Act
        // Normal: content status keeps its content-only data.
        val state = HomeUiState(
            status = ScreenUiStatus.CONTENT,
            memos = memos,
            tags = tags,
            allSelectedTagIds = setOf(TagId("tag-1")),
            allSelectedFavorite = true,
            bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
        )

        // Assert
        assertEquals(memos, state.memos)
    }

    @Test
    fun normalErrorKeepsSelectionAndSearchControls() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(MemoId("memo-1")))
        val search = SearchUiState(isActive = true, query = "shopping")

        // Act
        // Normal: control state survives a whole-screen error.
        val state = HomeUiState(
            status = ScreenUiStatus.ERROR,
            selection = selection,
            search = search,
            selectedFilter = HomeFilterUiState.Favorite
        )

        // Assert
        assertEquals(selection, state.selection)
    }

    @Test
    fun errorConstructorThrowsWhenMemosRemainOutsideContent() {
        // Act & Assert
        // Error: memos cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            HomeUiState(status = ScreenUiStatus.ERROR, memos = listOf(memoUiModel()))
        }
    }

    @Test
    fun errorConstructorThrowsWhenTagsRemainOutsideContent() {
        // Act & Assert
        // Error: tags cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            HomeUiState(status = ScreenUiStatus.LOADING, tags = listOf(tagUiModel()))
        }
    }

    @Test
    fun errorConstructorThrowsWhenBulkTagDialogIsVisibleOutsideContent() {
        // Act & Assert
        // Error: the bulk tag dialog cannot be visible without content status.
        assertThrows(IllegalArgumentException::class.java) {
            HomeUiState(
                status = ScreenUiStatus.ERROR,
                bulkTagDialog = HomeBulkTagDialogUiState(isVisible = true)
            )
        }
    }

    @Test
    fun errorConstructorThrowsWhenAllSelectedTagIdsRemainOutsideContent() {
        // Act & Assert
        // Error: derived selection tags cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            HomeUiState(
                status = ScreenUiStatus.ERROR,
                allSelectedTagIds = setOf(TagId("tag-1"))
            )
        }
    }

    @Test
    fun errorConstructorThrowsWhenAllSelectedFavoriteRemainsOutsideContent() {
        // Act & Assert
        // Error: derived favorite flag cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            HomeUiState(status = ScreenUiStatus.ERROR, allSelectedFavorite = true)
        }
    }

    private fun memoUiModel() = MemoUiModel(
        id = MemoId("memo-1"),
        title = "Shopping",
        body = "Buy coffee",
        tags = emptyList(),
        updatedAtMillis = 1_000L,
        isFavorite = false
    )

    private fun tagUiModel() = TagUiModel(
        id = TagId("tag-1"),
        name = "Work",
        colorArgb = 0xFF000000
    )

}
