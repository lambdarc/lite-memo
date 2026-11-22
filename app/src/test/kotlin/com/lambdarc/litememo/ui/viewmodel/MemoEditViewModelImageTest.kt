package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.ViewModelStore
import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.exception.MemoImageTooLargeException
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.ui.model.MemoImageUiModel
import com.lambdarc.litememo.ui.state.MemoEditUiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MemoEditViewModelImageTest {

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
    fun normalUiStateLoadsPersistedImageMetadata() = runTest(dispatcher) {
        // Arrange
        val memo =
            memoFixture(images = listOf(memoImageFixture(id = "loaded", fileName = "loaded.jpg")))
        val viewModel = memoEditViewModel(memo = memo)

        // Act
        // Normal: loaded images retain their metadata and are already persisted.
        advanceUntilIdle()

        // Assert
        assertEquals(
            MemoImageUiModel("loaded", "loaded.jpg", "/images/loaded.jpg", true),
            viewModel.uiState.value.images.single()
        )
    }

    @Test
    fun coroutineViewModelClearDeletesImageWhoseCopyCompletesDuringCancellation() =
        runTest(dispatcher) {
            // Arrange
            val imageStore = SequencedMemoImageStore()
            val viewModel = memoEditViewModel(memoImageStore = imageStore)
            val store = ViewModelStore().apply { put("memo-edit", viewModel) }
            advanceUntilIdle()
            viewModel.attachImages(
                listOf("content://images/1", "content://images/2", "content://images/3")
            )
            runCurrent()
            imageStore.secondSaveStarted.await()

            // Act
            // Coroutine/Interaction: destruction cleans a completed copy before it reaches UI state.
            store.clear()
            imageStore.releaseSecondSave.complete(Unit)
            advanceUntilIdle()

            // Assert
            assertAll(
                {
                    assertEquals(
                        listOf(MemoImageFileName("image-1.jpg"), MemoImageFileName("image-2.jpg")),
                        imageStore.deletedFileNames
                    )
                },
                {
                    assertEquals(
                        listOf("content://images/1", "content://images/2"),
                        imageStore.savedSources.map { it.value }
                    )
                }
            )
        }

    @Test
    fun normalAttachImagesAddsImagesToUiState() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel()
        advanceUntilIdle()

        // Act
        // Normal: attached image copies are exposed in edit UI state.
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()

        // Assert
        assertEquals(
            MemoImageUiModel("image-1", "image-1.jpg", "/images/image-1.jpg", false),
            viewModel.uiState.value.images.single()
        )
    }

    @Test
    fun flowAttachImagesTriggersAutosaveWithImages() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Flow: image edits join the existing debounce autosave path.
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(
            listOf("image-1.jpg"),
            memoRepository.savedMemos.single().images.map {
                it.fileName.value
            }
        )
    }

    @Test
    fun normalRemoveImageExcludesImageFromNextSave() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(images = listOf(memoImageFixture(fileName = "image-1.jpg")))
        val memoRepository = FakeMemoRepository(listOf(memo))
        val viewModel = memoEditViewModel(memo = memo, memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Normal: removed persisted images are omitted from the next SaveMemoCommand.
        viewModel.removeImage("image-1")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(
            emptyList<String>(),
            memoRepository.savedMemos.single().images.map {
                it.fileName.value
            }
        )
    }

    @Test
    fun interactionRemoveImageDeletesCopiedFileWhenImageIsNotPersisted() = runTest(dispatcher) {
        // Arrange
        val imageStore = FakeMemoImageStore()
        val viewModel = memoEditViewModel(memoImageStore = imageStore)
        advanceUntilIdle()
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()

        // Act
        // Interaction: unsaved images are invisible to repository diff cleanup, so VM deletes them.
        viewModel.removeImage("image-1")
        advanceUntilIdle()

        // Assert
        assertEquals(listOf(MemoImageFileName("image-1.jpg")), imageStore.deletedFileNames)
    }

    @Test
    fun interactionRemoveImageDoesNotDeleteFileImmediatelyWhenImageIsPersisted() =
        runTest(dispatcher) {
            // Arrange
            val imageStore = FakeMemoImageStore()
            val memo = memoFixture(images = listOf(memoImageFixture(fileName = "image-1.jpg")))
            val viewModel = memoEditViewModel(memo = memo, memoImageStore = imageStore)
            advanceUntilIdle()

            // Act
            // Interaction: persisted image file deletion is deferred to the repository save diff.
            viewModel.removeImage("image-1")
            advanceUntilIdle()

            // Assert
            assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames)
        }

    @Test
    fun stateTransitionPersistMarksImagesAsPersisted() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel()
        advanceUntilIdle()
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()

        // Act
        // StateTransition: successful autosave flips attached images to persisted.
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(true, viewModel.uiState.value.images.single().isPersisted)
    }

    @Test
    fun coroutineSavingSnapshotMarksOnlySavedImagesAsPersisted() = runTest(dispatcher) {
        // Arrange
        val memoRepository = BlockingSaveMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()

        // Act
        // Coroutine: images attached while a save is suspended are not part of that save.
        viewModel.flushEdits()
        runCurrent()
        memoRepository.saveStarted.await()
        viewModel.attachImages(listOf("content://images/2"))
        runCurrent()
        memoRepository.releaseSave.complete(Unit)
        runCurrent()

        // Assert
        assertEquals(
            listOf(
                MemoImageUiModel("image-1", "image-1.jpg", "/images/image-1.jpg", true),
                MemoImageUiModel("image-2", "image-2.jpg", "/images/image-2.jpg", false)
            ),
            viewModel.uiState.value.images
        )
    }

    @Test
    fun coroutineRemoveImageDoesNotDeleteFileWhileSnapshotSaveIsRunning() = runTest(dispatcher) {
        // Arrange
        val memoRepository = BlockingSaveMemoRepository()
        val imageStore = FakeMemoImageStore()
        val viewModel = memoEditViewModel(
            memoRepository = memoRepository,
            memoImageStore = imageStore
        )
        advanceUntilIdle()
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()

        // Act
        // Coroutine: snapshot-owned files are kept for repository diff cleanup.
        viewModel.flushEdits()
        runCurrent()
        memoRepository.saveStarted.await()
        viewModel.removeImage("image-1")
        runCurrent()
        memoRepository.releaseSave.complete(Unit)
        runCurrent()

        // Assert
        assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames)
    }

    @Test
    fun stateTransitionFinishEditingPersistsImageOnlyMemoInsteadOfDiscarding() =
        runTest(dispatcher) {
            // Arrange
            val memoRepository = FakeMemoRepository()
            val viewModel = memoEditViewModel(memoRepository = memoRepository)
            advanceUntilIdle()
            viewModel.attachImages(listOf("content://images/1"))
            runCurrent()

            // Act
            // StateTransition: an image-only memo is content and is saved before leaving.
            viewModel.finishEditing()
            advanceUntilIdle()

            // Assert
            val result = viewModel.uiResults.value.single()
            assertAll(
                { assertInstanceOf(MemoEditUiResult.NavigateBack::class.java, result) },
                {
                    assertEquals(
                        listOf("image-1.jpg"),
                        memoRepository.savedMemos.single().images.map { it.fileName.value }
                    )
                }
            )
        }

    @Test
    fun errorAttachImagesRetainsImageAttachFailedWhenStoreThrows() = runTest(dispatcher) {
        // Arrange
        val imageStore = FakeMemoImageStore().apply {
            saveError = IllegalStateException("copy failed")
        }
        val viewModel = memoEditViewModel(memoImageStore = imageStore)
        advanceUntilIdle()

        // Act
        // Error/Flow: image copy failures surface in the pending result queue.
        viewModel.attachImages(listOf("content://images/1"))
        advanceUntilIdle()

        // Assert
        assertInstanceOf(
            MemoEditUiResult.ImageAttachFailed::class.java,
            viewModel.uiResults.value.single()
        )
    }

    @Test
    fun errorAttachImagesReportsTooLargeImageSeparately() = runTest(dispatcher) {
        // Arrange
        val imageStore = FakeMemoImageStore().apply {
            saveError = MemoImageTooLargeException(maxBytes = 32L)
        }
        val viewModel = memoEditViewModel(memoImageStore = imageStore)
        advanceUntilIdle()

        // Act
        // Error/Flow: an oversized image is reported with its own reason, not as a copy failure.
        viewModel.attachImages(listOf("content://images/1"))
        advanceUntilIdle()

        // Assert
        assertInstanceOf(
            MemoEditUiResult.ImageTooLarge::class.java,
            viewModel.uiResults.value.single()
        )
    }

    @Test
    fun interactionAttachImagesIsIgnoredWhileMemoIsLoading() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val imageStore = FakeMemoImageStore()
        val viewModel = memoEditViewModel(
            memo = memo,
            memoRepository = BlockingGetMemoRepository(memo),
            memoImageStore = imageStore
        )
        runCurrent()

        // Act
        // Interaction: no image is copied before the memo becomes editable
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()

        // Assert
        assertEquals(emptyList<ImageSourceReference>(), imageStore.savedSources)
    }

    @Test
    fun interactionAttachCompletingAfterFinishDeletesCopiedImages() = runTest(dispatcher) {
        // Arrange
        val imageStore = SequencedMemoImageStore()
        val viewModel = memoEditViewModel(memoImageStore = imageStore)
        advanceUntilIdle()
        viewModel.attachImages(listOf("content://images/1", "content://images/2"))
        runCurrent()
        imageStore.secondSaveStarted.await()
        viewModel.finishEditing()
        advanceUntilIdle()

        // Act
        // Interaction: copies that finish after leaving the editor are removed, not attached
        imageStore.releaseSecondSave.complete(Unit)
        advanceUntilIdle()

        // Assert
        assertAll(
            {
                assertEquals(
                    listOf(MemoImageFileName("image-1.jpg"), MemoImageFileName("image-2.jpg")),
                    imageStore.deletedFileNames
                )
            },
            { assertEquals(emptyList<MemoImageUiModel>(), viewModel.uiState.value.images) }
        )
    }

    @Test
    fun interactionRemoveImageAfterFinishEditingKeepsCopyBeingSaved() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val imageStore = FakeMemoImageStore()
        val viewModel = memoEditViewModel(
            memoRepository = memoRepository,
            memoImageStore = imageStore
        )
        advanceUntilIdle()
        viewModel.attachImages(listOf("content://images/1"))
        runCurrent()
        viewModel.finishEditing()

        // Act
        // Interaction: an unsaved copy queued for the closing save is not deleted
        viewModel.removeImage("image-1")
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames) },
            {
                assertEquals(
                    listOf("image-1.jpg"),
                    memoRepository.savedMemos.single().images.map { it.fileName.value }
                )
            }
        )
    }

    @Test
    fun boundaryRemoveImageWithUnknownIdKeepsState() = runTest(dispatcher) {
        // Arrange
        val imageStore = FakeMemoImageStore()
        val memo = memoFixture(images = listOf(memoImageFixture(fileName = "image-1.jpg")))
        val viewModel = memoEditViewModel(memo = memo, memoImageStore = imageStore)
        advanceUntilIdle()

        // Act
        // Boundary: an ID that matches no image changes nothing
        viewModel.removeImage("missing")
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(listOf("image-1"), viewModel.uiState.value.images.map { it.id }) },
            { assertEquals(false, viewModel.uiState.value.isModified) },
            { assertEquals(emptyList<MemoImageFileName>(), imageStore.deletedFileNames) }
        )
    }
}
