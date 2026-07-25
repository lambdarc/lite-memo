package com.appvoyager.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appvoyager.litememo.domain.model.ApplyMemoBulkActionCommand
import com.appvoyager.litememo.domain.model.MemoBulkAction
import com.appvoyager.litememo.domain.model.MemoFilter
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.usecase.ApplyMemoBulkActionUseCase
import com.appvoyager.litememo.domain.usecase.FilterMemosUseCase
import com.appvoyager.litememo.domain.usecase.FormatMemoTextUseCase
import com.appvoyager.litememo.domain.usecase.ObserveMemosUseCase
import com.appvoyager.litememo.domain.usecase.ObserveTagsUseCase
import com.appvoyager.litememo.domain.usecase.ResolveMemoImagePathUseCase
import com.appvoyager.litememo.domain.usecase.SearchMemosUseCase
import com.appvoyager.litememo.domain.usecase.SetMemoFavoriteUseCase
import com.appvoyager.litememo.ui.model.MemoUiModel
import com.appvoyager.litememo.ui.model.TagUiModel
import com.appvoyager.litememo.ui.state.HomeBulkTagDialogUiState
import com.appvoyager.litememo.ui.state.HomeFilterUiState
import com.appvoyager.litememo.ui.state.HomeUiState
import com.appvoyager.litememo.ui.state.MemoSearchUiStateHolder
import com.appvoyager.litememo.ui.state.MemoSelectionUiState
import com.appvoyager.litememo.ui.state.SearchUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val observeMemosUseCase: ObserveMemosUseCase,
    private val observeTagsUseCase: ObserveTagsUseCase,
    private val filterMemosUseCase: FilterMemosUseCase,
    private val searchMemosUseCase: SearchMemosUseCase,
    private val setMemoFavoriteUseCase: SetMemoFavoriteUseCase,
    private val applyMemoBulkActionUseCase: ApplyMemoBulkActionUseCase,
    private val formatMemoTextUseCase: FormatMemoTextUseCase,
    private val resolveMemoImagePathUseCase: ResolveMemoImagePathUseCase
) : ViewModel() {

    private val selectedFilter = MutableStateFlow<HomeFilterUiState>(HomeFilterUiState.All)
    private val memoSearch = MemoSearchUiStateHolder(searchMemosUseCase)
    private val selection = MutableStateFlow(MemoSelectionUiState())
    private val bulkTagDialog = MutableStateFlow(HomeBulkTagDialogUiState())
    private val retryTrigger = MutableStateFlow(false)
    private var isBulkActionInFlight = false

    private val _actionErrorEvent = Channel<Unit>(Channel.CONFLATED)
    val actionErrorEvent = _actionErrorEvent.receiveAsFlow()

    private val uiControls = combine(
        selectedFilter,
        memoSearch.controls,
        selection,
        bulkTagDialog
    ) { filter, search, activeSelection, tagDialog ->
        HomeUiControls(
            filter = filter,
            search = search,
            selection = activeSelection,
            tagDialog = tagDialog
        )
    }

    val uiState: StateFlow<HomeUiState> = retryTrigger.flatMapLatest {
        combine(
            observeMemosUseCase(),
            observeTagsUseCase(),
            uiControls,
            memoSearch.results
        ) { memos, tags, controls, searchResult ->
            val tagUiModels = tags.map { TagUiModel.fromDomain(it) }
            val effectiveFilter = controls.filter.effectiveFilter(tags)
            val filteredMemos = filterMemosUseCase(memos, effectiveFilter.toDomainFilter())
            val memoById = memos.associateBy { it.id }
            val selectedMemos = controls.selection.selectedMemoIds.mapNotNull(memoById::get)
            val allSelectedFavorite = controls.selection.selectedMemoIds.isNotEmpty() &&
                controls.selection.selectedMemoIds.all { memoById[it]?.isFavorite == true }
            val allSelectedTagIds = selectedMemos
                .takeIf {
                    it.isNotEmpty() && it.size == controls.selection.selectedMemoIds.size
                }
                ?.map { memo -> memo.tagIds.toSet() }
                ?.reduce { commonTagIds, tagIds -> commonTagIds intersect tagIds }
                ?: emptySet()
            val search = memoSearch.toUiState(controls.search, searchResult) { searchHits ->
                MemoUiModel.fromDomain(
                    searchHits,
                    tags,
                    resolveMemoImagePathUseCase::invoke
                )
            }

            HomeUiState(
                isLoading = false,
                selectedFilter = effectiveFilter,
                search = search,
                selection = controls.selection,
                allSelectedFavorite = allSelectedFavorite,
                allSelectedTagIds = allSelectedTagIds,
                bulkTagDialog = controls.tagDialog,
                tags = tagUiModels,
                memos = MemoUiModel.fromDomain(
                    filteredMemos,
                    tags,
                    resolveMemoImagePathUseCase::invoke
                )
            )
        }.catch {
            emit(
                HomeUiState(
                    isLoading = false,
                    hasError = true,
                    selectedFilter = selectedFilter.value,
                    search = memoSearch.controls.value,
                    selection = selection.value,
                    bulkTagDialog = bulkTagDialog.value
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState()
    )

    fun selectFilter(filter: HomeFilterUiState) {
        selectedFilter.value = filter
    }

    fun toggleSearch() {
        memoSearch.toggle()
    }

    fun updateSearchQuery(query: String) {
        memoSearch.updateQuery(query)
    }

    fun closeSearch() {
        memoSearch.close()
    }

    fun setMemoFavorite(memoId: MemoId, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                setMemoFavoriteUseCase(memoId, isFavorite)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _actionErrorEvent.trySend(Unit)
            }
        }
    }

    fun startSelection(memoId: MemoId) {
        bulkTagDialog.value = HomeBulkTagDialogUiState()
        selection.update { it.selectOnly(memoId) }
    }

    fun toggleMemoSelection(memoId: MemoId) {
        val next = selection.updateAndGet { it.toggle(memoId) }
        if (!next.isActive) {
            bulkTagDialog.value = HomeBulkTagDialogUiState()
        }
    }

    fun clearSelection() {
        selection.update { it.clear() }
        bulkTagDialog.value = HomeBulkTagDialogUiState()
    }

    fun moveSelectedMemosToTrash() {
        applySelectedMemoAction(MemoBulkAction.moveToTrash())
    }

    fun setSelectedMemosFavorite(isFavorite: Boolean) {
        applySelectedMemoAction(MemoBulkAction.setFavorite(isFavorite))
    }

    fun requestToggleTagForSelectedMemos() {
        if (!selection.value.isActive) return
        bulkTagDialog.value = HomeBulkTagDialogUiState(isVisible = true)
    }

    fun dismissBulkTagDialog() {
        bulkTagDialog.value = HomeBulkTagDialogUiState()
    }

    fun toggleSelectedMemosTag(tagId: TagId) {
        if (!bulkTagDialog.value.isVisible) return
        val action = if (tagId in uiState.value.allSelectedTagIds) {
            MemoBulkAction.removeTag(tagId)
        } else {
            MemoBulkAction.addTag(tagId)
        }
        bulkTagDialog.value = HomeBulkTagDialogUiState()
        applySelectedMemoAction(action)
    }

    fun formatMemoText(title: String, body: String): String? = formatMemoTextUseCase(title, body)

    fun getSelectedMemoForShare(): MemoUiModel? {
        val state = uiState.value
        val selectedId = state.selection.selectedMemoIds.singleOrNull() ?: return null
        return (state.memos + state.search.results).find { it.id == selectedId }
    }

    fun retry() {
        retryTrigger.update { !it }
    }

    private fun applySelectedMemoAction(action: MemoBulkAction) {
        val memoIds = selection.value.selectedMemoIds.toList()
        if (memoIds.isEmpty()) return
        if (isBulkActionInFlight) return
        isBulkActionInFlight = true

        viewModelScope.launch {
            try {
                applyMemoBulkActionUseCase(
                    ApplyMemoBulkActionCommand(
                        memoIds = memoIds,
                        action = action
                    )
                )
                clearSelection()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                bulkTagDialog.value = HomeBulkTagDialogUiState()
                _actionErrorEvent.trySend(Unit)
            } finally {
                isBulkActionInFlight = false
            }
        }
    }

    private fun HomeFilterUiState.toDomainFilter(): MemoFilter = when (this) {
        HomeFilterUiState.All -> MemoFilter.All
        HomeFilterUiState.Unorganized -> MemoFilter.Unorganized
        HomeFilterUiState.Favorite -> MemoFilter.Favorite
        is HomeFilterUiState.ByTag -> MemoFilter.ByTag(tagId)
    }

    private fun HomeFilterUiState.effectiveFilter(tags: List<Tag>): HomeFilterUiState {
        if (this is HomeFilterUiState.ByTag && tags.none { it.id == tagId }) {
            return HomeFilterUiState.All
        }
        return this
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private data class HomeUiControls(
    val filter: HomeFilterUiState,
    val search: SearchUiState,
    val selection: MemoSelectionUiState,
    val tagDialog: HomeBulkTagDialogUiState
)
