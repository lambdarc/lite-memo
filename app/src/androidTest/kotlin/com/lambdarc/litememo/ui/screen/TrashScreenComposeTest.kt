package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.R
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.ui.model.TrashedMemoUiModel
import com.lambdarc.litememo.ui.state.MemoSelectionUiState
import com.lambdarc.litememo.ui.state.TrashUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrashScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalLoadingShowsProgressWithoutErrorOrMenu() {
        // Act
        // Normal: loading displays progress without error or menu actions.
        setScreen(uiState = { TrashUiState.Loading })

        // Assert
        composeRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.retry_label)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.more_options)).assertDoesNotExist()
    }

    @Test
    fun normalErrorShowsMessageWithoutMenu() {
        // Act
        // Normal: error displays its message and retry without menu actions.
        setScreen(uiState = { TrashUiState.Error() })

        // Assert
        composeRule.onNodeWithText(string(R.string.unknown_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.retry_label)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.more_options)).assertDoesNotExist()
    }

    @Test
    fun interactionErrorRetryInvokesCallback() {
        // Arrange
        var retryCount = 0
        setScreen(
            uiState = { TrashUiState.Error() },
            onRetry = { retryCount += 1 }
        )

        // Act
        // Interaction: the retry button invokes the supplied callback.
        composeRule.onNodeWithText(string(R.string.retry_label)).performClick()

        // Assert
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun boundaryEmptyContentShowsEmptyMessage() {
        // Act
        // Boundary: empty trash displays its message without a loading or error state.
        setScreen(uiState = { TrashUiState.Content() })

        // Assert
        composeRule.onNodeWithText(string(R.string.trash_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.more_options)).assertDoesNotExist()
    }

    @Test
    fun stateTransitionContentSelectionCanBeClearedIndependently() {
        // Arrange
        var state by mutableStateOf(
            TrashUiState.Content(
                memos = listOf(memo()),
                selection = MemoSelectionUiState(setOf(MemoId("memo-1")))
            )
        )
        setScreen(uiState = { state }, onClearSelection = {
            state =
                state.copy(selection = state.selection.clear())
        })
        composeRule.onNodeWithText("Deleted memo").assertIsDisplayed()

        // Act
        // StateTransition: clearing selection keeps the trash content visible.
        composeRule.onNodeWithContentDescription(string(R.string.clear_selection)).performClick()

        composeRule.onNodeWithContentDescription(
            string(R.string.clear_selection)
        ).assertDoesNotExist()
        composeRule.onNodeWithText("Deleted memo").assertIsDisplayed()
    }

    @Test
    fun interactionErrorSelectionCanBeCleared() {
        // Arrange
        var state: TrashUiState by mutableStateOf(
            TrashUiState.Error(selection = MemoSelectionUiState(setOf(MemoId("memo-1"))))
        )
        setScreen(uiState = { state }, onClearSelection = { state = TrashUiState.Error() })

        // Act
        // Interaction: the selection kept during an error can still be cleared from the top bar.
        composeRule.onNodeWithContentDescription(string(R.string.clear_selection)).performClick()

        // Assert
        composeRule.onNodeWithContentDescription(
            string(R.string.clear_selection)
        ).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.retry_label)).assertIsDisplayed()
    }

    private fun setScreen(
        uiState: () -> TrashUiState,
        onRetry: () -> Unit = {
        },
        onClearSelection: () -> Unit = {}
    ) {
        val actions = object : TrashScreenActions {
            override fun onBackClick() = Unit
            override fun onMemoLongClick(memoId: MemoId) = Unit
            override fun onMemoSelectionToggle(memoId: MemoId) = Unit
            override fun onClearSelection() = onClearSelection.invoke()
            override fun onRestoreSelectedMemos() = Unit
            override fun onEmptyTrashRequest() = Unit
            override fun onConfirmEmptyTrash() = Unit
            override fun onDismissEmptyTrash() = Unit
            override fun onRetry() = onRetry.invoke()
        }
        composeRule.setContent {
            TestScreenContent { TrashScreen(uiState = uiState(), actions = actions) }
        }
    }

    private fun memo() = TrashedMemoUiModel(
        id = MemoId("memo-1"),
        title = "Deleted memo",
        body = "Body",
        tags = emptyList(),
        deletedAt = TimestampMillis(1_000L)
    )

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
