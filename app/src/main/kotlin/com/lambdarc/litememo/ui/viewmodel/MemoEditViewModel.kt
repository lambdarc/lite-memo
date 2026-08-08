package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.SaveMemoCommand
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoBody
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
import com.lambdarc.litememo.domain.model.value.MemoImageId
import com.lambdarc.litememo.domain.model.value.MemoTitle
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
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
import com.lambdarc.litememo.ui.model.TagUiModel
import com.lambdarc.litememo.ui.state.MemoEditUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class MemoEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getMemoUseCase: GetMemoUseCase,
    private val saveMemoUseCase: SaveMemoUseCase,
    private val moveMemoToTrashUseCase: MoveMemoToTrashUseCase,
    private val discardMemoUseCase: DiscardMemoUseCase,
    private val generateMemoIdUseCase: GenerateMemoIdUseCase,
    private val observeTagsUseCase: ObserveTagsUseCase,
    private val formatMemoTextUseCase: FormatMemoTextUseCase,
    private val attachMemoImageUseCase: AttachMemoImageUseCase,
    private val deleteMemoImagesUseCase: DeleteMemoImagesUseCase,
    private val resolveMemoImagePathUseCase: ResolveMemoImagePathUseCase
) : ViewModel() {

    private val initialMemoId: String? = savedStateHandle["memoId"]
    private val isNewMemoSession: Boolean = savedStateHandle
        .get<Boolean>(SESSION_STARTED_AS_NEW_KEY)
        ?: isRestoredNewMemoSession().also { startedAsNew ->
            savedStateHandle[SESSION_STARTED_AS_NEW_KEY] = startedAsNew
        }
    private var memoId: MemoId = initialMemoId
        ?.let { MemoId(it) }
        ?: savedStateHandle.get<String>(GENERATED_MEMO_ID_KEY)
            ?.let { MemoId(it) }
        ?: generateMemoIdUseCase().also { generatedId ->
            savedStateHandle[GENERATED_MEMO_ID_KEY] = generatedId.value
        }
    private val createdAtMillis: Long = savedStateHandle["createdAt"] ?: -1L
    private val createdAt: TimestampMillis? = if (createdAtMillis >= 0) {
        TimestampMillis(createdAtMillis)
    } else {
        null
    }

    private val _uiState = MutableStateFlow(
        MemoEditUiState(isLoading = initialMemoId != null, memoId = initialMemoId)
    )
    val uiState: StateFlow<MemoEditUiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<MemoEditNavigationUiEvent>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private val _operationErrorEvent = Channel<MemoEditOperationErrorUiEvent>(Channel.BUFFERED)
    val operationErrorEvent = _operationErrorEvent.receiveAsFlow()

    private val persistMutex = Mutex()
    private var autosaveJob: Job? = null
    private var isFinishing = false
    private var activePersistImageIds: Set<String> = emptySet()

    init {
        loadInitialState()
        observeTagsUseCase()
            .onEach { tags ->
                _uiState.update { state ->
                    val validTagIds = tags.map { it.id }.toSet()
                    state.copy(
                        availableTags = tags.map { TagUiModel.fromDomain(it) },
                        selectedTagIds = state.selectedTagIds.intersect(validTagIds)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun reload() {
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        loadInitialState()
    }

    fun formatMemoText(): String? {
        val state = _uiState.value
        return formatMemoTextUseCase(state.title, state.body)
    }

    fun updateTitle(title: String) {
        updateEditState { it.copy(title = title, isModified = true) }
    }

    fun updateBody(body: String) {
        updateEditState { it.copy(body = body, isModified = true) }
    }

    fun toggleTag(tagId: TagId) {
        updateEditState { state ->
            val selectedTagIds = if (tagId in state.selectedTagIds) {
                state.selectedTagIds - tagId
            } else {
                state.selectedTagIds + tagId
            }
            state.copy(selectedTagIds = selectedTagIds, isModified = true)
        }
    }

    fun attachImages(sourceUris: List<String>) {
        if (sourceUris.isEmpty()) return
        if (isFinishing || _uiState.value.isDeletePending) return
        viewModelScope.launch {
            val attached = mutableListOf<MemoImageUiModel>()
            var hasFailure = false
            sourceUris.forEach { uri ->
                try {
                    val image = attachMemoImageUseCase(ImageSourceReference(uri))
                    attached += MemoImageUiModel.fromDomain(
                        image = image,
                        resolveImagePath = resolveMemoImagePathUseCase::invoke,
                        isPersisted = false
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    hasFailure = true
                }
            }

            if (isFinishing || _uiState.value.isDeletePending) {
                deleteCopiedImagesQuietly(attached)
                return@launch
            }
            if (attached.isNotEmpty()) {
                updateEditState { state ->
                    state.copy(images = state.images + attached, isModified = true)
                }
            }
            if (hasFailure) {
                _operationErrorEvent.trySend(MemoEditOperationErrorUiEvent.ImageAttachFailed)
            }
        }
    }

    fun removeImage(imageId: String) {
        if (isFinishing || _uiState.value.isDeletePending) return
        val removed = _uiState.value.images.firstOrNull { it.id == imageId }
        if (removed != null) {
            updateEditState { state ->
                state.copy(images = state.images.filterNot { it.id == imageId }, isModified = true)
            }
            if (!removed.isPersisted && imageId !in activePersistImageIds) {
                viewModelScope.launch {
                    deleteCopiedImagesQuietly(listOf(removed))
                }
            }
        }
    }

    fun delete() {
        val targetMemoId = _uiState.value.memoId?.let { MemoId(it) } ?: return
        if (_uiState.value.isDeletePending || isFinishing) return
        isFinishing = true
        autosaveJob?.cancel()
        _uiState.update { state -> state.copy(isDeletePending = true, hasError = false) }
        viewModelScope.launch {
            persistMutex.withLock {
                try {
                    val deletedMemoId = moveMemoToTrashUseCase(targetMemoId)
                    clearSavedState()
                    _uiState.update { state -> state.copy(isDeletePending = false) }
                    _navigationEvent.trySend(MemoEditNavigationUiEvent.MemoDeleted(deletedMemoId))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    isFinishing = false
                    _uiState.update { state -> state.copy(isDeletePending = false) }
                    _operationErrorEvent.trySend(MemoEditOperationErrorUiEvent.DeleteFailed)
                }
            }
        }
    }

    fun finishEditing() {
        if (_uiState.value.isDeletePending || isFinishing) return
        isFinishing = true
        autosaveJob?.cancel()
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isContentBlank()) {
                finishBlankEditing()
                return@launch
            }
            if (persist()) {
                clearSavedState()
                _navigationEvent.trySend(MemoEditNavigationUiEvent.NavigateBack)
            } else {
                isFinishing = false
            }
        }
    }

    fun flushEdits() {
        if (_uiState.value.isDeletePending || isFinishing) return
        viewModelScope.launch {
            autosaveJob?.cancel()
            persist()
        }
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            val savedState = savedStateEdit()
            if (savedState != null) {
                _uiState.update { current ->
                    savedState.copy(availableTags = current.availableTags)
                }
                return@launch
            }

            val currentMemoId = initialMemoId ?: run {
                _uiState.update { it.copy(isLoading = false, hasError = false) }
                return@launch
            }
            try {
                val memo = getMemoUseCase(MemoId(currentMemoId))
                if (memo == null) {
                    _uiState.update { it.copy(isLoading = false, hasError = true) }
                    return@launch
                }
                _uiState.update { current ->
                    memo.toUiState().copy(availableTags = current.availableTags)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _uiState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    private fun updateEditState(transform: (MemoEditUiState) -> MemoEditUiState) {
        if (isFinishing || _uiState.value.isDeletePending) return
        val nextState = transform(_uiState.value)
        _uiState.value = nextState
        persistSavedState(nextState)
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            persist()
        }
    }

    // 保存・破棄・ゴミ箱送りは同一の [persistMutex] で直列化し、進行中の保存が完了した後に破棄／ゴミ箱送りが走ることを保証する。
    private suspend fun persist(): Boolean = persistMutex.withLock {
        val state = _uiState.value
        if (state.isContentBlank()) return@withLock true
        val savingImageIds = state.images.map { it.id }.toSet()
        val savingImages = state.images.map {
            MemoImage(
                id = MemoImageId(it.id),
                fileName = MemoImageFileName(it.fileName)
            )
        }
        activePersistImageIds = savingImageIds
        try {
            val memo = saveMemoUseCase(
                SaveMemoCommand(
                    id = memoId,
                    title = MemoTitle(state.title),
                    body = MemoBody(state.body),
                    createdAt = createdAt,
                    tagIds = state.selectedTagIds.toList(),
                    images = savingImages,
                    isFavorite = state.isFavorite
                )
            )
            applySavedMemo(memo, savingImageIds)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            _operationErrorEvent.trySend(MemoEditOperationErrorUiEvent.SaveFailed)
            false
        } finally {
            activePersistImageIds = emptySet()
        }
    }

    private suspend fun finishBlankEditing() {
        persistMutex.withLock {
            try {
                if (isNewMemoSession) {
                    discardMemoUseCase(memoId)
                    clearSavedState()
                    _navigationEvent.trySend(MemoEditNavigationUiEvent.NavigateBack)
                } else {
                    val deletedMemoId = moveMemoToTrashUseCase(memoId)
                    clearSavedState()
                    _navigationEvent.trySend(MemoEditNavigationUiEvent.MemoDeleted(deletedMemoId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                isFinishing = false
                _operationErrorEvent.trySend(MemoEditOperationErrorUiEvent.DeleteFailed)
            }
        }
    }

    private fun applySavedMemo(memo: Memo, savedImageIds: Set<String>) {
        memoId = memo.id
        savedStateHandle["memoId"] = memo.id.value
        _uiState.update { state ->
            val nextState = state.copy(
                memoId = memo.id.value,
                isLoading = false,
                hasError = false,
                images = state.images.map { image ->
                    if (image.id in savedImageIds) image.copy(isPersisted = true) else image
                }
            )
            persistSavedState(nextState)
            nextState
        }
    }

    private fun savedStateEdit(): MemoEditUiState? {
        if (!savedStateHandle.contains(EDIT_TITLE_KEY)) return null
        val title = savedStateHandle.get<String>(EDIT_TITLE_KEY).orEmpty()
        val body = savedStateHandle.get<String>(EDIT_BODY_KEY).orEmpty()
        val tagIds = savedStateHandle.get<ArrayList<String>>(EDIT_TAG_IDS_KEY)
            .orEmpty().map { TagId(it) }.toSet()
        val isFavorite = savedStateHandle.get<Boolean>(EDIT_IS_FAVORITE_KEY) ?: false
        val imageIds = savedStateHandle.get<ArrayList<String>>(EDIT_IMAGE_IDS_KEY).orEmpty()
        val imageFileNames = savedStateHandle
            .get<ArrayList<String>>(EDIT_IMAGE_FILE_NAMES_KEY)
            .orEmpty()
        val imagePersistedFlags = savedStateHandle
            .get<ArrayList<Boolean>>(EDIT_IMAGE_PERSISTED_FLAGS_KEY)
            .orEmpty()
        val images = imageIds.mapIndexedNotNull { index, id ->
            val fileName = imageFileNames.getOrNull(index) ?: return@mapIndexedNotNull null
            MemoImageUiModel(
                id = id,
                fileName = fileName,
                filePath = resolveMemoImagePathUseCase(MemoImageFileName(fileName)),
                isPersisted = imagePersistedFlags.getOrNull(index) ?: false
            )
        }

        return MemoEditUiState(
            isLoading = false,
            memoId = initialMemoId,
            title = title,
            body = body,
            isFavorite = isFavorite,
            isModified = true,
            selectedTagIds = tagIds,
            images = images
        )
    }

    private fun persistSavedState(state: MemoEditUiState) {
        savedStateHandle[EDIT_TITLE_KEY] = state.title
        savedStateHandle[EDIT_BODY_KEY] = state.body
        savedStateHandle[EDIT_TAG_IDS_KEY] = ArrayList(state.selectedTagIds.map { it.value })
        savedStateHandle[EDIT_IS_FAVORITE_KEY] = state.isFavorite
        savedStateHandle[EDIT_IMAGE_IDS_KEY] = ArrayList(state.images.map { it.id })
        savedStateHandle[EDIT_IMAGE_FILE_NAMES_KEY] = ArrayList(state.images.map { it.fileName })
        savedStateHandle[EDIT_IMAGE_PERSISTED_FLAGS_KEY] = ArrayList(
            state.images.map { it.isPersisted }
        )
    }

    private fun clearSavedState() {
        savedStateHandle.remove<String>(EDIT_TITLE_KEY)
        savedStateHandle.remove<String>(EDIT_BODY_KEY)
        savedStateHandle.remove<ArrayList<String>>(EDIT_TAG_IDS_KEY)
        savedStateHandle.remove<Boolean>(EDIT_IS_FAVORITE_KEY)
        savedStateHandle.remove<ArrayList<String>>(EDIT_IMAGE_IDS_KEY)
        savedStateHandle.remove<ArrayList<String>>(EDIT_IMAGE_FILE_NAMES_KEY)
        savedStateHandle.remove<ArrayList<Boolean>>(EDIT_IMAGE_PERSISTED_FLAGS_KEY)
    }

    private fun isRestoredNewMemoSession(): Boolean {
        if (initialMemoId == null) return true
        return savedStateHandle.get<String>(GENERATED_MEMO_ID_KEY) == initialMemoId
    }

    private fun Memo.toUiState() = MemoEditUiState(
        isLoading = false,
        memoId = id.value,
        title = title.value,
        body = body.value,
        isFavorite = isFavorite,
        selectedTagIds = tagIds.toSet(),
        images = images.map { image ->
            MemoImageUiModel.fromDomain(
                image = image,
                resolveImagePath = resolveMemoImagePathUseCase::invoke,
                isPersisted = true
            )
        }
    )

    private suspend fun deleteCopiedImagesQuietly(images: List<MemoImageUiModel>) {
        if (images.isEmpty()) return
        try {
            deleteMemoImagesUseCase(images.map { MemoImageFileName(it.fileName) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    private fun MemoEditUiState.isContentBlank(): Boolean =
        title.isBlank() && body.isBlank() && images.isEmpty()

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MILLIS = 1_000L
        const val EDIT_TITLE_KEY = "editTitle"
        const val EDIT_BODY_KEY = "editBody"
        const val EDIT_TAG_IDS_KEY = "editTagIds"
        const val EDIT_IS_FAVORITE_KEY = "editIsFavorite"
        const val EDIT_IMAGE_IDS_KEY = "editImageIds"
        const val EDIT_IMAGE_FILE_NAMES_KEY = "editImageFileNames"
        const val EDIT_IMAGE_PERSISTED_FLAGS_KEY = "editImagePersistedFlags"
        const val GENERATED_MEMO_ID_KEY = "generatedMemoId"
        const val SESSION_STARTED_AS_NEW_KEY = "sessionStartedAsNew"
    }

}
