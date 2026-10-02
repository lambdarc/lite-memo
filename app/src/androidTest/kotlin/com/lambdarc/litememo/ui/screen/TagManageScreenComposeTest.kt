package com.lambdarc.litememo.ui.screen

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
import com.lambdarc.litememo.ui.state.ScreenUiStatus
import com.lambdarc.litememo.ui.state.TagEditUiState
import com.lambdarc.litememo.ui.state.TagManageUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagManageScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalLoadingShowsProgressWithoutCreateAction() {
        // Act
        // Normal: loading displays progress without error or create actions.
        setScreen(uiState = { TagManageUiState() })

        // Assert
        composeRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.retry_label)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.tag_create)).assertDoesNotExist()
    }

    @Test
    fun normalErrorShowsMessageWithoutCreateAction() {
        // Act
        // Normal: error displays its message and retry without the create action.
        setScreen(uiState = { TagManageUiState(status = ScreenUiStatus.ERROR) })

        // Assert
        composeRule.onNodeWithText(string(R.string.unknown_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.retry_label)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.tag_create)).assertDoesNotExist()
    }

    @Test
    fun interactionErrorRetryInvokesCallback() {
        // Arrange
        var retryCount = 0
        setScreen(
            uiState = { TagManageUiState(status = ScreenUiStatus.ERROR) },
            onRetry = { retryCount += 1 }
        )

        // Act
        // Interaction: the retry button invokes the supplied callback.
        composeRule.onNodeWithText(string(R.string.retry_label)).performClick()

        // Assert
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun boundaryEmptyContentShowsEmptyMessageAndCreateAction() {
        // Act
        // Boundary: empty tags remain content with the create action.
        setScreen(uiState = { TagManageUiState(status = ScreenUiStatus.CONTENT) })

        // Assert
        composeRule.onNodeWithText(string(R.string.tag_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.tag_create)).assertIsDisplayed()
    }

    @Test
    fun normalContentRetainsIndependentEditingInput() {
        // Act
        // Normal: the editing draft remains independent from the screen status.
        setScreen(uiState = {
            TagManageUiState(
                status = ScreenUiStatus.CONTENT,
                tags = listOf(testTagUiModel(name = "Work")),
                editingTag = TagEditUiState(name = "Draft")
            )
        })

        // Assert
        composeRule.onNodeWithText("Draft").assertIsDisplayed()
    }

    private fun setScreen(uiState: () -> TagManageUiState, onRetry: () -> Unit = {}) {
        composeRule.setContent {
            TestScreenContent {
                TagManageScreen(
                    uiState = uiState(),
                    onBackClick = {},
                    onCreateClick = {},
                    onEditClick = {},
                    onDeleteRequest = {},
                    onConfirmDelete = {},
                    onDismissDelete = {},
                    onEditNameChange = {},
                    onEditColorSelect = {},
                    onSaveEdit = {},
                    onCancelEdit = {},
                    onRetry = onRetry
                )
            }
        }
    }

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
}
