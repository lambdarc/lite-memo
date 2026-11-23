package com.lambdarc.litememo.ui.viewmodel

import com.lambdarc.litememo.domain.FakeMemoImageStore
import com.lambdarc.litememo.domain.FakeMemoRepository
import com.lambdarc.litememo.domain.FakeTagRepository
import com.lambdarc.litememo.domain.MutableTimeProvider
import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.repository.FakeDisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.usecase.ApplyMemoBulkActionUseCase
import com.lambdarc.litememo.domain.usecase.FilterMemosUseCase
import com.lambdarc.litememo.domain.usecase.FormatMemoTextUseCase
import com.lambdarc.litememo.domain.usecase.ObserveMemosUseCase
import com.lambdarc.litememo.domain.usecase.ObserveTagsUseCase
import com.lambdarc.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.lambdarc.litememo.domain.usecase.SearchMemosUseCase
import com.lambdarc.litememo.ui.state.HomeUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertInstanceOf
import java.time.Instant

internal val HOME_TEST_TODAY_MILLIS: Long =
    Instant.parse("2026-05-11T12:00:00Z").toEpochMilli()

internal fun TestScope.collectUiState(viewModel: HomeViewModel) {
    backgroundScope.launch { viewModel.uiState.collect {} }
}

internal suspend fun HomeViewModel.firstContent(
    predicate: (HomeUiState.Content) -> Boolean = { true }
): HomeUiState.Content = uiState.filterIsInstance<HomeUiState.Content>().first(predicate)

internal fun HomeUiState.asContent(): HomeUiState.Content =
    assertInstanceOf(HomeUiState.Content::class.java, this)

internal fun TestScope.openBulkTagDialog(viewModel: HomeViewModel, memoId: MemoId) {
    backgroundScope.launch { viewModel.uiState.collect {} }
    runCurrent()
    viewModel.startSelection(memoId)
    viewModel.requestToggleTagForSelectedMemos()
    runCurrent()
}

internal fun homeViewModel(
    memos: List<Memo> = emptyList(),
    tags: List<Tag> = emptyList(),
    memoRepository: MemoRepository = FakeMemoRepository(memos),
    tagRepository: TagRepository = FakeTagRepository(tags),
    imageStore: MemoImageStore = FakeMemoImageStore()
): HomeViewModel {
    val displaySettingsRepository = FakeDisplaySettingsRepository()
    return HomeViewModel(
        observeMemosUseCase = ObserveMemosUseCase(memoRepository, displaySettingsRepository),
        observeTagsUseCase = ObserveTagsUseCase(tagRepository),
        filterMemosUseCase = FilterMemosUseCase(),
        searchMemosUseCase = SearchMemosUseCase(memoRepository, displaySettingsRepository),
        applyMemoBulkActionUseCase = ApplyMemoBulkActionUseCase(
            memoRepository = memoRepository,
            tagRepository = tagRepository,
            currentTimeProvider = MutableTimeProvider(TimestampMillis(HOME_TEST_TODAY_MILLIS + 1))
        ),
        formatMemoTextUseCase = FormatMemoTextUseCase(),
        resolveMemoImagePathUseCase = ResolveMemoImagePathUseCase(imageStore)
    )
}

internal class FailableMemoRepository(
    memos: List<Memo>,
    private val delegate: FakeMemoRepository = FakeMemoRepository(memos)
) : MemoRepository by delegate {
    private val failure = MutableStateFlow<Throwable?>(null)

    fun fail() {
        failure.value = IllegalStateException("Failed to observe memos.")
    }

    fun recover() {
        failure.value = null
    }

    override fun observeActiveMemos(): Flow<List<Memo>> =
        combine(delegate.observeActiveMemos(), failure) { memos, error ->
            if (error != null) throw error
            memos
        }
}

internal class SaveFailingMemoRepository(memo: Memo) :
    MemoRepository by FakeMemoRepository(listOf(memo)) {

    override suspend fun saveMemo(memo: Memo, expectedMemo: Memo?): Unit =
        error("Failed to save memo.")

    override suspend fun saveActiveMemoBulkWrites(writes: List<ActiveMemoBulkWrite>): Unit =
        error("Failed to save active memos.")
}
