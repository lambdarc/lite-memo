package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.QueueMemoIdProvider
import com.lambdarc.litememo.domain.memoFixture
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.tagFixture
import com.lambdarc.litememo.domain.usecase.AttachMemoImageUseCase
import com.lambdarc.litememo.domain.usecase.DeleteMemoImagesUseCase
import com.lambdarc.litememo.domain.usecase.DiscardMemoUseCase
import com.lambdarc.litememo.domain.usecase.FormatMemoTextUseCase
import com.lambdarc.litememo.domain.usecase.GenerateMemoIdUseCase
import com.lambdarc.litememo.domain.usecase.GetMemoUseCase
import com.lambdarc.litememo.domain.usecase.MoveMemoToTrashUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.lambdarc.litememo.domain.usecase.SaveMemoUseCase
import com.lambdarc.litememo.ui.model.MemoImageUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
class MemoEditViewModelTest {

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
            { assertEquals("Saved title", state.title) },
            { assertEquals("Saved body", state.body) },
            { assertEquals(setOf(TagId("tag-1")), state.selectedTagIds) },
            { assertEquals(true, state.isFavorite) }
        )
    }

    @Test
    fun normalUiStateLoadsExistingMemo() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(
            id = "memo-1",
            title = "Existing title",
            body = "Existing body",
            tagIds = listOf(TagId("tag-1")),
            isFavorite = true
        )
        val viewModel = memoEditViewModel(memo = memo)

        // Act
        // Normal: existing memo edit starts from Room.
        advanceUntilIdle()
        val state = viewModel.uiState.value

        // Assert
        assertAll(
            { assertEquals(false, state.isLoading) },
            { assertEquals("Existing title", state.title) },
            { assertEquals("Existing body", state.body) },
            { assertEquals(setOf(TagId("tag-1")), state.selectedTagIds) },
            { assertEquals(true, state.isFavorite) }
        )
    }

    @Test
    fun flowBackDuringExistingMemoLoadNavigatesWithoutDeletingOrSaving() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val repository = BlockingGetMemoRepository(memo)
        val imageStore = FakeMemoImageStore()
        val viewModel = memoEditViewModel(
            memo = memo,
            memoRepository = repository,
            memoImageStore = imageStore
        )
        runCurrent()

        // Act & Assert
        // Flow/Interaction: back during loading leaves the existing memo untouched.
        viewModel.navigationEvent.test {
            viewModel.updateTitle("Ignored")
            viewModel.attachImages(listOf("content://images/ignored"))
            viewModel.finishEditing()
            assertEquals(MemoEditNavigationUiEvent.NavigateBack, awaitItem())
        }

        // Assert
        assertAll(
            { assertEquals(emptyList<Memo>(), repository.delegate.savedMemos) },
            {
                assertEquals(
                    emptyList<MemoId>(),
                    repository.delegate.movedToTrash.map { it.memoId }
                )
            },
            { assertEquals(emptyList<ImageSourceReference>(), imageStore.savedSources) }
        )
    }

    @Test
    fun flowBackAfterExistingMemoLoadFailureNavigatesWithoutDeleting() = runTest(dispatcher) {
        // Arrange
        val repository = GetFailingMemoRepository()
        val viewModel = memoEditViewModel(
            savedStateHandle = SavedStateHandle(mapOf("memoId" to "memo-1")),
            memoRepository = repository
        )
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.hasError)

        // Act & Assert
        // Flow/Interaction: leaving a load error does not treat blank UI state as deletion.
        viewModel.navigationEvent.test {
            viewModel.finishEditing()
            assertEquals(MemoEditNavigationUiEvent.NavigateBack, awaitItem())
        }
        assertEquals(emptyList<MemoId>(), repository.delegate.movedToTrash.map { it.memoId })
    }

    @Test
    fun stateTransitionTagFailureSurvivesLaterMemoLoadAndRetryKeepsInput() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1", title = "Existing")
        val memoRepository = BlockingGetMemoRepository(memo)
        val tagRepository = RetryableTagRepository()
        val viewModel = memoEditViewModel(
            memo = memo,
            memoRepository = memoRepository,
            tagRepository = tagRepository
        )
        runCurrent()
        assertEquals(true, viewModel.uiState.value.hasTagError)
        memoRepository.releaseGet.complete(Unit)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.value.hasTagError)
        viewModel.updateTitle("Draft")

        // Act
        // StateTransition: tag retry replaces only tag state and keeps active memo input.
        tagRepository.allowObservation()
        viewModel.retryTags()
        advanceUntilIdle()

        // Assert
        assertAll(
            { assertEquals(false, viewModel.uiState.value.hasTagError) },
            { assertEquals("Draft", viewModel.uiState.value.title) },
            {
                assertEquals(
                    listOf("Recovered tag"),
                    viewModel.uiState.value.availableTags.map {
                        it.name
                    }
                )
            }
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
    fun normalAutosavePersistsAfterDebounce() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Normal: non-empty edits are saved silently after debounce.
        viewModel.updateTitle("Autosaved")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals("Autosaved", memoRepository.savedMemos.single().title.value)
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

            // Act & Assert
            // StateTransition: an image-only memo is content and is saved before leaving.
            viewModel.navigationEvent.test {
                viewModel.finishEditing()
                advanceUntilIdle()
                val event = awaitItem()
                assertAll(
                    { assertEquals(MemoEditNavigationUiEvent.NavigateBack, event) },
                    {
                        assertEquals(
                            listOf("image-1.jpg"),
                            memoRepository.savedMemos.single().images.map { it.fileName.value }
                        )
                    }
                )
            }
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
    fun errorAttachImagesEmitsImageAttachFailedWhenStoreThrows() = runTest(dispatcher) {
        // Arrange
        val imageStore = FakeMemoImageStore().apply {
            saveError = IllegalStateException("copy failed")
        }
        val viewModel = memoEditViewModel(memoImageStore = imageStore)
        advanceUntilIdle()

        // Act & Assert
        // Error/Flow: image copy failures surface through the image attach event.
        viewModel.operationErrorEvent.test {
            viewModel.attachImages(listOf("content://images/1"))
            advanceUntilIdle()
            assertEquals(MemoEditOperationErrorUiEvent.ImageAttachFailed, awaitItem())
        }
    }

    @Test
    fun coroutineRapidEditsPersistOnlyLatestValue() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Coroutine: a new edit before debounce cancels the previous autosave.
        viewModel.updateTitle("First")
        advanceTimeBy(500L.milliseconds)
        viewModel.updateTitle("Second")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(listOf("Second"), memoRepository.savedMemos.map { it.title.value })
    }

    @Test
    fun coroutineBlankContentIsNeverAutosaved() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Coroutine: blank title and body do not create a Room row.
        viewModel.updateTitle(" ")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(emptyList<Memo>(), memoRepository.savedMemos)
    }

    @Test
    fun stateTransitionAutosaveReusesSameMemoIdWithinSession() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // StateTransition: generated id is fixed at editor session start.
        viewModel.updateTitle("Title")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()
        viewModel.updateBody("Body")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(
            listOf(MemoId("generated-id")),
            memoRepository.savedMemos
                .map { it.id }
                .distinct()
        )
    }

    @Test
    fun normalExistingMemoAutosaveUsesNavigationMemoId() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val memoRepository = FakeMemoRepository(listOf(memo))
        val viewModel = memoEditViewModel(memo = memo, memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Normal: existing memo updates keep the navigation argument id.
        viewModel.updateTitle("Updated")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(MemoId("memo-1"), memoRepository.savedMemos.single().id)
    }

    @Test
    fun flowBlankNewMemoFinishNavigatesBackWithoutRow() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act & Assert
        // Flow: empty new memo is discarded silently.
        viewModel.navigationEvent.test {
            viewModel.finishEditing()
            advanceUntilIdle()
            val event = awaitItem()
            assertAll(
                { assertEquals(MemoEditNavigationUiEvent.NavigateBack, event) },
                { assertEquals(emptyList<MemoId>(), memoRepository.currentMemos().map { it.id }) }
            )
        }
    }

    @Test
    fun flowPersistedNewMemoEmptiedOnFinishIsHardDeleted() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()
        viewModel.updateTitle("Title")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Act & Assert
        // Flow: a new memo that becomes blank before leaving is removed from Room.
        viewModel.navigationEvent.test {
            viewModel.updateTitle("")
            viewModel.finishEditing()
            advanceUntilIdle()
            val event = awaitItem()
            assertAll(
                { assertEquals(MemoEditNavigationUiEvent.NavigateBack, event) },
                { assertEquals(emptyList<MemoId>(), memoRepository.currentMemos().map { it.id }) }
            )
        }
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

        // Act & Assert
        // Flow: restored sessions that started as new still discard instead of trashing.
        viewModel.navigationEvent.test {
            viewModel.finishEditing()
            advanceUntilIdle()
            val event = awaitItem()
            assertAll(
                { assertEquals(MemoEditNavigationUiEvent.NavigateBack, event) },
                { assertEquals(emptyList<MemoId>(), memoRepository.currentMemos().map { it.id }) },
                {
                    assertEquals(emptyList<MemoId>(), memoRepository.movedToTrash.map { it.memoId })
                }
            )
        }
    }

    @Test
    fun flowExistingMemoEmptiedOnFinishMovesToTrash() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val memoRepository = FakeMemoRepository(listOf(memo))
        val viewModel = memoEditViewModel(memo = memo, memoRepository = memoRepository)
        advanceUntilIdle()

        // Act & Assert
        // Flow: blanking an existing memo is treated as deletion with undo support.
        viewModel.navigationEvent.test {
            viewModel.updateTitle("")
            viewModel.updateBody("")
            viewModel.finishEditing()
            advanceUntilIdle()
            assertEquals(MemoEditNavigationUiEvent.MemoDeleted(memo.id), awaitItem())
        }
    }

    @Test
    fun flowFinishFlushesPendingEditBeforeNavigateBack() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act & Assert
        // Flow: explicit back persists pending edits without waiting for debounce.
        viewModel.navigationEvent.test {
            viewModel.updateTitle("Pending")
            viewModel.finishEditing()
            advanceUntilIdle()
            val event = awaitItem()
            assertAll(
                { assertEquals(MemoEditNavigationUiEvent.NavigateBack, event) },
                { assertEquals("Pending", memoRepository.savedMemos.single().title.value) }
            )
        }
    }

    @Test
    fun coroutineFinishEditingIgnoresLaterTextChanges() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Coroutine: once finish starts, late UI updates must not re-arm autosave.
        viewModel.updateTitle("Pending")
        viewModel.finishEditing()
        viewModel.updateBody("Late body")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        val memo = memoRepository.savedMemos.single()
        assertAll(
            { assertEquals("Pending", memo.title.value) },
            { assertEquals("", memo.body.value) },
            { assertEquals(emptySet<TagId>(), memo.tagIds.toSet()) },
            { assertEquals(false, memo.isFavorite) }
        )
    }

    @Test
    fun flowFinishSaveFailureKeepsDraftWithoutNavigating() = runTest(dispatcher) {
        // Arrange
        val savedStateHandle = SavedStateHandle()
        val viewModel = memoEditViewModel(
            savedStateHandle = savedStateHandle,
            memoRepository = SaveFailingMemoRepository()
        )
        advanceUntilIdle()

        // Act & Assert
        // Flow/Error: finish-time save failure keeps the draft and does not navigate away.
        viewModel.navigationEvent.test {
            viewModel.updateTitle("Unsaved")
            viewModel.finishEditing()
            advanceUntilIdle()
            expectNoEvents()
        }

        // Assert
        assertEquals("Unsaved", savedStateHandle.get<String>("editTitle"))
    }

    @Test
    fun coroutineFlushEditsPersistsWithoutNavigation() = runTest(dispatcher) {
        // Arrange
        val memoRepository = FakeMemoRepository()
        val viewModel = memoEditViewModel(memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Coroutine: ON_STOP flush saves but does not close the editor.
        viewModel.updateTitle("Background")
        viewModel.flushEdits()
        advanceUntilIdle()

        // Assert
        assertEquals("Background", memoRepository.savedMemos.single().title.value)
    }

    @Test
    fun coroutineDeleteCancelsPendingAutosave() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val memoRepository = FakeMemoRepository(listOf(memo))
        val viewModel = memoEditViewModel(memo = memo, memoRepository = memoRepository)
        advanceUntilIdle()

        // Act
        // Coroutine: delete prevents delayed autosave from recreating the memo.
        viewModel.updateTitle("Pending")
        viewModel.delete()
        viewModel.updateBody("Late body")
        advanceTimeBy(1_000L.milliseconds)
        advanceUntilIdle()

        // Assert
        assertEquals(emptyList<Memo>(), memoRepository.savedMemos)
    }

    @Test
    fun flowAutosaveFailureEmitsSaveFailed() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel(memoRepository = SaveFailingMemoRepository())
        advanceUntilIdle()

        // Act & Assert
        // Flow/Error: autosave failure is surfaced as the existing save error event.
        viewModel.operationErrorEvent.test {
            viewModel.updateTitle("Title")
            advanceTimeBy(1_000L.milliseconds)
            advanceUntilIdle()
            assertEquals(MemoEditOperationErrorUiEvent.SaveFailed, awaitItem())
        }
    }

    @Test
    fun flowRapidFinishEditingNavigatesBackOnce() = runTest(dispatcher) {
        // Arrange
        val viewModel = memoEditViewModel()
        advanceUntilIdle()

        // Act & Assert
        // Flow: finishing guard prevents duplicate navigation events.
        viewModel.navigationEvent.test {
            viewModel.updateTitle("Title")
            viewModel.finishEditing()
            viewModel.finishEditing()
            advanceUntilIdle()
            assertEquals(MemoEditNavigationUiEvent.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun flowDeleteEmitsMemoDeletedEventWithDeletedMemo() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val viewModel = memoEditViewModel(memo = memo)
        advanceUntilIdle()

        // Act & Assert
        // Flow: toolbar delete keeps the existing delete event contract.
        viewModel.navigationEvent.test {
            viewModel.delete()
            advanceUntilIdle()
            assertEquals(MemoEditNavigationUiEvent.MemoDeleted(memo.id), awaitItem())
        }
    }

    @Test
    fun flowDeleteFailureEmitsOperationError() = runTest(dispatcher) {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val viewModel = memoEditViewModel(
            memo = memo,
            memoRepository = MoveToTrashFailingMemoRepository(memo)
        )
        advanceUntilIdle()

        // Act & Assert
        // Flow/Error: delete failure emits the existing delete error event.
        viewModel.operationErrorEvent.test {
            viewModel.delete()
            advanceUntilIdle()
            assertEquals(MemoEditOperationErrorUiEvent.DeleteFailed, awaitItem())
        }
    }

    private fun memoEditViewModel(
        memo: Memo? = null,
        savedStateHandle: SavedStateHandle = memo?.let {
            SavedStateHandle(
                mapOf(
                    "memoId" to it.id.value,
                    "createdAt" to it.createdAt.value
                )
            )
        } ?: SavedStateHandle(),
        memoRepository: MemoRepository = FakeMemoRepository(listOfNotNull(memo)),
        memoImageStore: MemoImageStore = FakeMemoImageStore(),
        tagRepository: TagRepository = FakeTagRepository(listOf(tagFixture(id = "tag-1")))
    ): MemoEditViewModel {
        val timeProvider = MutableTimeProvider(TimestampMillis(2_000L))
        return MemoEditViewModel(
            savedStateHandle = savedStateHandle,
            getMemoUseCase = GetMemoUseCase(memoRepository),
            saveMemoUseCase = SaveMemoUseCase(
                memoRepository = memoRepository,
                tagRepository = tagRepository,
                memoIdProvider = QueueMemoIdProvider(listOf(MemoId("unused-id"))),
                currentTimeProvider = timeProvider
            ),
            moveMemoToTrashUseCase = MoveMemoToTrashUseCase(
                memoRepository = memoRepository,
                currentTimeProvider = timeProvider
            ),
            discardMemoUseCase = DiscardMemoUseCase(memoRepository),
            generateMemoIdUseCase = GenerateMemoIdUseCase(
                QueueMemoIdProvider(listOf(MemoId("generated-id")))
            ),
            observeTagsUseCase = ObserveTagsUseCase(tagRepository),
            formatMemoTextUseCase = FormatMemoTextUseCase(),
            attachMemoImageUseCase = AttachMemoImageUseCase(memoImageStore),
            deleteMemoImagesUseCase = DeleteMemoImagesUseCase(memoImageStore),
            resolveMemoImagePathUseCase = ResolveMemoImagePathUseCase(memoImageStore)
        )
    }

    private class SaveFailingMemoRepository : MemoRepository by FakeMemoRepository() {
        override suspend fun saveMemo(memo: Memo): Unit = error("Failed to save memo.")
    }

    private class GetFailingMemoRepository(
        val delegate: FakeMemoRepository = FakeMemoRepository()
    ) : MemoRepository by delegate {
        override suspend fun getActiveMemo(id: MemoId): Memo? = error("Failed to load memo.")
    }

    private class BlockingGetMemoRepository(
        private val memo: Memo,
        val delegate: FakeMemoRepository = FakeMemoRepository(listOf(memo))
    ) : MemoRepository by delegate {
        val releaseGet = CompletableDeferred<Unit>()

        override suspend fun getActiveMemo(id: MemoId): Memo? {
            releaseGet.await()
            return memo
        }
    }

    private class RetryableTagRepository : TagRepository {
        private var observationAllowed = false
        private val recoveredTag = tagFixture(id = "tag-recovered", name = "Recovered tag")

        fun allowObservation() {
            observationAllowed = true
        }

        override fun observeTags(): Flow<List<Tag>> = if (observationAllowed) {
            flowOf(listOf(recoveredTag))
        } else {
            flow { error("Failed to load tags.") }
        }

        override suspend fun getTag(id: TagId): Tag? = null
        override suspend fun findTagByName(
            name: com.lambdarc.litememo.domain.model.value.TagName
        ): Tag? = null
        override suspend fun getTagsByIds(ids: List<TagId>): List<Tag> = emptyList()
        override suspend fun saveTag(tag: Tag) = Unit
        override suspend fun deleteTag(id: TagId) = Unit
        override suspend fun getAllTags(): List<Tag> = emptyList()
    }

    private class SequencedMemoImageStore : MemoImageStore {
        val secondSaveStarted = CompletableDeferred<Unit>()
        val releaseSecondSave = CompletableDeferred<Unit>()
        val savedSources = mutableListOf<ImageSourceReference>()
        val deletedFileNames = mutableListOf<MemoImageFileName>()

        override suspend fun saveImage(source: ImageSourceReference): MemoImage {
            savedSources += source
            val number = savedSources.size
            if (number == 2) {
                secondSaveStarted.complete(Unit)
                releaseSecondSave.await()
            }
            return memoImageFixture(id = "image-$number", fileName = "image-$number.jpg")
        }

        override suspend fun deleteImages(fileNames: List<MemoImageFileName>) {
            deletedFileNames += fileNames
        }

        override fun resolveImagePath(fileName: MemoImageFileName): String =
            "/images/${fileName.value}"
    }

    private class MoveToTrashFailingMemoRepository(memo: Memo) :
        MemoRepository by FakeMemoRepository(listOf(memo)) {
        override suspend fun moveMemoToTrash(id: MemoId, deletedAt: TimestampMillis): Unit =
            error("Failed to move memo to trash.")
    }

    private class BlockingSaveMemoRepository(
        private val delegate: FakeMemoRepository = FakeMemoRepository()
    ) : MemoRepository by delegate {

        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()

        override suspend fun saveMemo(memo: Memo) {
            saveStarted.complete(Unit)
            releaseSave.await()
            delegate.saveMemo(memo)
        }
    }
}
