package com.lambdarc.litememo.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.QueueMemoIdProvider
import com.lambdarc.litememo.domain.memoImageFixture
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoImage
import com.lambdarc.litememo.domain.model.value.ImageSourceReference
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.MemoImageFileName
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
import kotlinx.coroutines.CompletableDeferred

internal fun memoEditViewModel(
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

internal class BlockingGetMemoRepository(
    private val memo: Memo,
    val delegate: FakeMemoRepository = FakeMemoRepository(listOf(memo))
) : MemoRepository by delegate {
    val releaseGet = CompletableDeferred<Unit>()

    override suspend fun getActiveMemo(id: MemoId): Memo? {
        releaseGet.await()
        return memo
    }
}

internal class SequencedMemoImageStore : MemoImageStore {
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

    override fun resolveImagePath(fileName: MemoImageFileName): String = "/images/${fileName.value}"
}

internal class BlockingSaveMemoRepository(
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
