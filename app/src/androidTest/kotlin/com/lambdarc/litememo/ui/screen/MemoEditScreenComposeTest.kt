package com.lambdarc.litememo.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lambdarc.litememo.R
import com.lambdarc.litememo.ui.state.MemoEditUiState
import com.lambdarc.litememo.ui.state.ScreenUiStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoEditScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun normalLoadingHidesEditorAndContentActions() {
        // Act
        // Normal: loading shows progress while editing and content actions are absent.
        setMemoEditScreen(
            uiState = { MemoEditUiState(status = ScreenUiStatus.LOADING, memoId = "memo-1") }
        )

        // Assert
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        assertEditorAndActionsAbsent()
    }

    @Test
    fun normalErrorHidesEditorAndContentActions() {
        // Act
        // Normal: the screen load error shows its retry action without editing controls.
        setMemoEditScreen(
            uiState = { MemoEditUiState(status = ScreenUiStatus.ERROR, memoId = "memo-1") }
        )

        // Assert
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.retry_label)).assertIsDisplayed()
        assertEditorAndActionsAbsent()
    }

    @Test
    fun interactionErrorRetryInvokesCallback() {
        // Arrange
        var retried = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        setMemoEditScreen(
            uiState = { MemoEditUiState(status = ScreenUiStatus.ERROR, memoId = "memo-1") },
            onRetry = { retried = true }
        )

        // Act
        // Interaction: the retry button invokes the supplied callback.
        composeRule.onNodeWithText(context.getString(R.string.retry_label)).performClick()

        // Assert
        assertEquals(true, retried)
    }

    @Test
    fun normalContentWithTagErrorKeepsEditorEnabled() {
        // Act
        // Normal: an independent tag load error leaves memo editing and attachment available.
        setMemoEditScreen(uiState = { MemoEditUiState(hasTagError = true) })

        // Assert
        composeRule.onNodeWithTag(MemoEditTestTags.TITLE_INPUT).assertIsEnabled()
        composeRule.onNodeWithTag(MemoEditTestTags.BODY_INPUT).assertIsEnabled()
        composeRule.onNodeWithTag(MemoEditTestTags.ATTACH_IMAGE_BUTTON).assertIsEnabled()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithText(context.getString(R.string.memo_edit_tag_load_error))
            .assertIsDisplayed()
    }

    private fun assertEditorAndActionsAbsent() {
        composeRule.onNodeWithTag(MemoEditTestTags.TITLE_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(MemoEditTestTags.BODY_INPUT).assertDoesNotExist()
        composeRule.onNodeWithTag(MemoEditTestTags.ATTACH_IMAGE_BUTTON).assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.onNodeWithContentDescription(context.getString(R.string.more_options))
            .assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete_memo))
            .assertDoesNotExist()
    }

    @Test
    fun normalTitleInputAcceptsText() {
        // Arrange
        var uiState by mutableStateOf(MemoEditUiState())
        setMemoEditScreen(
            uiState = { uiState },
            onTitleChange = { title -> uiState = uiState.copy(title = title) }
        )

        // Act
        // Normal: a title edit is reflected through its callback.
        composeRule
            .onNodeWithTag(MemoEditTestTags.TITLE_INPUT)
            .performTextInput("Shopping")

        // Assert
        assertEquals("Shopping", uiState.title)
    }

    @Test
    fun normalBodyInputAcceptsText() {
        // Arrange
        var uiState by mutableStateOf(MemoEditUiState())
        setMemoEditScreen(
            uiState = { uiState },
            onBodyChange = { body -> uiState = uiState.copy(body = body) }
        )

        // Act
        // Normal: a body edit is reflected through its callback.
        composeRule
            .onNodeWithTag(MemoEditTestTags.BODY_INPUT)
            .performTextInput("Milk")

        // Assert
        assertEquals("Milk", uiState.body)
    }

    @Test
    fun interactionAttachImageButtonInvokesCallback() {
        // Arrange
        var clicked = false
        setMemoEditScreen(onAttachImageRequest = { clicked = true })

        // Act
        // Interaction: tapping the toolbar image button requests Photo Picker launch.
        composeRule
            .onNodeWithTag(MemoEditTestTags.ATTACH_IMAGE_BUTTON)
            .performClick()

        // Assert
        assertEquals(true, clicked)
    }

    @Test
    fun normalImagesShowImageListAndItems() {
        // Arrange
        val image = testMemoImageUiModel()

        // Act
        setMemoEditScreen(uiState = { MemoEditUiState(images = listOf(image)) })

        // Assert
        composeRule
            .onNodeWithTag(MemoEditTestTags.IMAGE_LIST)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(MemoEditTestTags.imageItem(image.id))
            .assertIsDisplayed()
    }

    @Test
    fun interactionRemoveImageButtonInvokesCallback() {
        // Arrange
        val image = testMemoImageUiModel()
        var removedId: String? = null
        setMemoEditScreen(
            uiState = { MemoEditUiState(images = listOf(image)) },
            onImageRemove = { removedId = it }
        )

        // Act
        // Interaction: tapping the per-image remove button emits that image id.
        composeRule
            .onNodeWithTag(MemoEditTestTags.removeImageButton(image.id))
            .performClick()

        // Assert
        assertEquals(image.id, removedId)
    }

    @Test
    fun normalDeletePendingHidesAttachImageButton() {
        // Act
        setMemoEditScreen(uiState = { MemoEditUiState(isDeletePending = true) })

        // Assert
        composeRule
            .onAllNodesWithTag(MemoEditTestTags.ATTACH_IMAGE_BUTTON)
            .assertCountEquals(0)
    }

    @Test
    fun stateTransitionDeletePendingDisablesFormAndFailureEnablesItAgain() {
        // Arrange
        val tag = testTagUiModel()
        val image = testMemoImageUiModel()
        var uiState by mutableStateOf(
            MemoEditUiState(
                memoId = "memo-1",
                title = "Title",
                body = "Body",
                availableTags = listOf(tag),
                images = listOf(image)
            )
        )
        setMemoEditScreen(uiState = { uiState })

        // Act
        // StateTransition: pending deletion disables every edit control until it finishes.
        composeRule.runOnIdle { uiState = uiState.copy(isDeletePending = true) }

        // Assert
        composeRule.onNodeWithTag(MemoEditTestTags.TITLE_INPUT).assertIsNotEnabled()
        composeRule.onNodeWithTag(MemoEditTestTags.BODY_INPUT).assertIsNotEnabled()
        composeRule.onNodeWithText(tag.name).assertIsNotEnabled()
        composeRule.onNodeWithTag(MemoEditTestTags.removeImageButton(image.id)).assertIsNotEnabled()

        // Act
        // StateTransition: a failed deletion restores form interaction without losing input.
        composeRule.runOnIdle { uiState = uiState.copy(isDeletePending = false) }

        // Assert
        composeRule.onNodeWithTag(MemoEditTestTags.TITLE_INPUT).assertIsEnabled()
        composeRule.onNodeWithTag(MemoEditTestTags.BODY_INPUT).assertIsEnabled()
        composeRule.onNodeWithText(tag.name).assertIsEnabled()
        composeRule.onNodeWithTag(MemoEditTestTags.removeImageButton(image.id)).assertIsEnabled()
    }

    private fun setMemoEditScreen(
        uiState: () -> MemoEditUiState = { MemoEditUiState() },
        onTitleChange: (String) -> Unit = {},
        onBodyChange: (String) -> Unit = {},
        onAttachImageRequest: () -> Unit = {},
        onImageRemove: (String) -> Unit = {},
        onRetry: () -> Unit = {}
    ) {
        composeRule.setContent {
            TestScreenContent {
                MemoEditScreen(
                    uiState = uiState(),
                    onTitleChange = onTitleChange,
                    onBodyChange = onBodyChange,
                    onTagToggle = {},
                    onDelete = {},
                    onBackRequest = {},
                    onRetry = onRetry,
                    onRetryTags = {},
                    onAttachImageRequest = onAttachImageRequest,
                    onImageRemove = onImageRemove,
                    onShareMemo = {}
                )
            }
        }
    }
}
