package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.ui.model.TrashedMemoUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TrashUiStateTest {

    @Test
    fun normalContentKeepsMemosAndDialog() {
        // Arrange
        val memos = listOf(trashedMemo())

        // Act
        // Normal: content status keeps its memos and dialog.
        val state = TrashUiState(
            status = ScreenUiStatus.CONTENT,
            memos = memos,
            showEmptyTrashDialog = true
        )

        // Assert
        assertEquals(memos, state.memos)
    }

    @Test
    fun errorConstructorThrowsWhenMemosRemainOutsideContent() {
        // Act & Assert
        // Error: memos cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            TrashUiState(status = ScreenUiStatus.ERROR, memos = listOf(trashedMemo()))
        }
    }

    @Test
    fun errorConstructorThrowsWhenEmptyTrashDialogRemainsOutsideContent() {
        // Act & Assert
        // Error: the empty trash dialog cannot remain without content status.
        assertThrows(IllegalArgumentException::class.java) {
            TrashUiState(status = ScreenUiStatus.ERROR, showEmptyTrashDialog = true)
        }
    }

    private fun trashedMemo() = TrashedMemoUiModel(
        id = MemoId("memo-1"),
        title = "Deleted memo",
        body = "Body",
        tags = emptyList(),
        deletedAt = TimestampMillis(1_000L)
    )

}
