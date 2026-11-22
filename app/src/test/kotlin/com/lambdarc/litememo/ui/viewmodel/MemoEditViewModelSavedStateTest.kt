package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.TrashMoveRecord
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.ui.model.MemoImageUiModel
import com.lambdarc.litememo.ui.state.MemoEditUiResult
import com.lambdarc.litememo.ui.state.ScreenUiStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoEditViewModelSavedStateTest {

    private lateinit var dispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun normalUiStateRestoresSavedStateEdit() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "editTitle" to "Saved title",
                    "editBody" to "Saved body",
                    "editTagIds" to arrayListOf("tag-1"),
                    "editIsFavorite" to true
                )
            )
        )
        advanceUntilIdle()

        // Act
        // Normal: SavedStateHandle edits win over database and empty defaults.
        val state = viewModel.uiState.value

        // Assert
        assertAll(
            { assertEquals(ScreenUiStatus.CONTENT, state.status) },
            { assertEquals("Saved title", state.title) },
            { assertEquals("Saved body", state.body) },
            { assertEquals(setOf(TagId("tag-1")), state.selectedTagIds) },
            { assertEquals(true, state.isFavorite) }
        )
    }

    @Test
    fun normalSavedStateEditRestoresImagesWithPersistedFlags() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "editTitle" to "Saved title",
                    "editBody" to "Saved body",
                    "editImageIds" to arrayListOf("image-1"),
                    "editImageFileNames" to arrayListOf("image-1.jpg"),
                    "editImagePersistedFlags" to arrayListOf(true)
                )
            )
        )
        advanceUntilIdle()

        // Act
        // Normal: SavedStateHandle image ids, file names, and persisted flags are restored by index.
        val image = viewModel.uiState.value.images.single()

        // Assert
        assertEquals(
            MemoImageUiModel("image-1", "image-1.jpg", "/images/image-1.jpg", true),
            image
        )
    }

    @Test
    fun flowRestoredNewMemoEmptiedOnFinishIsStillHardDeleted() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository(listOf(memoFixture(id = "generated-id")))
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "memoId" to "generated-id",
                    "generatedMemoId" to "generated-id",
                    "editTitle" to "",
                    "editBody" to ""
                )
            ),
            memoRepository = memoRepository
        )
        advanceUntilIdle()

        // Act
        // Flow: restored sessions that started as new still discard instead of trashing.
        viewModel.finishEditing()
        advanceUntilIdle()
        // Assert
        val result = viewModel.uiResults.value.single()
        assertAll(
            { assertInstanceOf(MemoEditUiResult.NavigateBack::class.java, result) },
            { assertEquals(emptyList<MemoId>(), memoRepository.currentMemos().map { it.id }) },
            {
                assertEquals(emptyList<MemoId>(), memoRepository.movedToTrash.map { it.memoId })
            }
        )
    }

    @Test
    fun normalRestoredNewMemoReusesGeneratedMemoId() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("generatedMemoId" to "restored-id")),
            memoRepository = memoRepository
        )
        advanceUntilIdle()

        // Act
        // Normal: a new memo restored after process death keeps the ID generated before it
        viewModel.updateTitle("Restored")
        advanceUntilIdle()

        // Assert
        assertEquals(MemoId("restored-id"), memoRepository.savedMemos.single().id)
    }

    @Test
    fun flowRestoredSessionFlagDiscardsBlankMemoInsteadOfTrashing() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository(listOf(memoFixture(id = "memo-1")))
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "memoId" to "memo-1",
                    "sessionStartedAsNew" to true,
                    "editTitle" to "",
                    "editBody" to ""
                )
            ),
            memoRepository = memoRepository
        )
        advanceUntilIdle()

        // Act
        // Flow: the saved new-session flag wins over inferring it from the memo ID
        viewModel.finishEditing()
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(listOf(MemoId("memo-1")), memoRepository.discardedIds) },
            { assertEquals(emptyList<TrashMoveRecord>(), memoRepository.movedToTrash) }
        )
    }

    @Test
    fun boundarySavedStateEditSkipsImageWithoutFileName() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "editTitle" to "Saved title",
                    "editImageIds" to arrayListOf("image-1", "image-2"),
                    "editImageFileNames" to arrayListOf("image-1.jpg"),
                    "editImagePersistedFlags" to arrayListOf(true, true)
                )
            )
        )
        advanceUntilIdle()

        // Act
        // Boundary: an image ID beyond the restored file names is dropped
        val images = viewModel.uiState.value.images

        // Assert
        assertEquals(listOf("image-1"), images.map { it.id })
    }

    @Test
    fun boundarySavedStateEditTreatsMissingPersistedFlagAsUnsaved() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "editTitle" to "Saved title",
                    "editImageIds" to arrayListOf("image-1", "image-2"),
                    "editImageFileNames" to arrayListOf("image-1.jpg", "image-2.jpg"),
                    "editImagePersistedFlags" to arrayListOf(true)
                )
            )
        )
        advanceUntilIdle()

        // Act
        // Boundary: an image without a restored flag is treated as an unsaved copy
        val images = viewModel.uiState.value.images

        // Assert
        assertEquals(listOf(true, false), images.map { it.isPersisted })
    }

    @Test
    fun boundarySavedStateEditWithOnlyTitleRestoresEmptyBody() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("editTitle" to "Saved title"))
        )
        advanceUntilIdle()

        // Act
        // Boundary: the title key alone is enough to restore an edit session
        val state = viewModel.uiState.value

        // Assert
        assertAll(
            { assertEquals("Saved title", state.title) },
            { assertEquals("", state.body) }
        )
    }
}
