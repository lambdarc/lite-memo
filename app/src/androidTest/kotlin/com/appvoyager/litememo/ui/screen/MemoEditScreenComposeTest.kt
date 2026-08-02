package com.appvoyager.litememo.ui.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.appvoyager.litememo.ui.state.MemoEditUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoEditScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    private fun setMemoEditScreen(
        uiState: () -> MemoEditUiState = { MemoEditUiState() },
        onTitleChange: (String) -> Unit = {},
        onBodyChange: (String) -> Unit = {},
        onAttachImageRequest: () -> Unit = {},
        onImageRemove: (String) -> Unit = {}
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
                    onRetry = {},
                    onAttachImageRequest = onAttachImageRequest,
                    onImageRemove = onImageRemove,
                    onShareMemo = {}
                )
            }
        }
    }
}
