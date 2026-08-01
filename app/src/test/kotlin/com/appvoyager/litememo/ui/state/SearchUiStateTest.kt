package com.appvoyager.litememo.ui.state

import app.cash.turbine.test
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.value.MemoId
import com.appvoyager.litememo.domain.repository.FakeDisplaySettingsRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.usecase.SearchMemosUseCase
import com.appvoyager.litememo.ui.model.MemoUiModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUiStateTest {

    @Test
    fun normalConstructorKeepsActiveQuery() {
        // Act
        // Normal: active search keeps its query.
        val search = SearchUiState(
            isActive = true,
            query = "shopping"
        )

        // Assert
        assertEquals("shopping", search.query)
    }

    @Test
    fun normalConstructorKeepsActiveResults() {
        // Arrange
        val results = listOf(memoUiModel())

        // Act
        // Normal: active search keeps its results.
        val search = SearchUiState(
            isActive = true,
            results = results
        )

        // Assert
        assertEquals(results, search.results)
    }

    @Test
    fun errorConstructorThrowsWhenInactiveQueryIsNotEmpty() {
        // Act & Assert
        // Error: inactive search cannot retain a query.
        assertThrows(IllegalArgumentException::class.java) {
            SearchUiState(query = "shopping")
        }
    }

    @Test
    fun errorConstructorThrowsWhenInactiveResultsAreNotEmpty() {
        // Arrange
        val results = listOf(memoUiModel())

        // Act & Assert
        // Error: inactive search cannot retain results.
        assertThrows(IllegalArgumentException::class.java) {
            SearchUiState(results = results)
        }
    }

    @Test
    fun stateTransitionOpenActivatesBlankSearch() {
        // Arrange
        val search = SearchUiState()

        // Act
        // StateTransition: open activates a clean search snapshot.
        val opened = search.opened()

        // Assert
        assertEquals(SearchUiState(isActive = true), opened)
    }

    @Test
    fun stateTransitionToggleClosesActiveSearch() {
        // Arrange
        val search = SearchUiState().opened().withQuery("shopping")

        // Act
        // StateTransition: toggling active search performs a complete reset.
        val toggled = search.toggled()

        // Assert
        assertEquals(SearchUiState(), toggled)
    }

    @Test
    fun stateTransitionCloseResetsActiveSearch() {
        // Arrange
        val search = SearchUiState().opened().withQuery("shopping")

        // Act
        // StateTransition: close restores the inactive invariant atomically.
        val closed = search.closed()

        // Assert
        assertEquals(SearchUiState(), closed)
    }

    @Test
    fun boundaryInactiveUpdateQueryIsIgnored() {
        // Arrange
        val search = SearchUiState()

        // Act
        // Boundary: query input outside active search is a no-op.
        val updated = search.withQuery("shopping")

        // Assert
        assertEquals(SearchUiState(), updated)
    }

    @Test
    fun boundaryInactiveSearchDiscardsResult() {
        // Arrange
        val search = SearchUiState()

        // Act
        // Boundary: a late result for a closed search cannot resurrect it.
        val updated = search.withResult(
            MemoSearchUiResult.Success(query = "shopping", memos = listOf(memoFixture()))
        ) { listOf(memoUiModel()) }

        // Assert
        assertEquals(SearchUiState(), updated)
    }

    @Test
    fun boundaryStaleQueryResultIsDiscarded() {
        // Arrange
        val search = SearchUiState().opened().withQuery("second")

        // Act
        // Boundary: a result for a previous query does not populate the current one.
        val updated = search.withResult(
            MemoSearchUiResult.Success(query = "first", memos = listOf(memoFixture()))
        ) { listOf(memoUiModel()) }

        // Assert
        assertEquals(emptyList<MemoUiModel>(), updated.results)
    }

    @Test
    fun normalMatchingQueryResultBecomesResults() {
        // Arrange
        val search = SearchUiState().opened().withQuery("shopping")
        val mapped = listOf(memoUiModel())

        // Act
        // Normal: a result for the current query is mapped into the UI state.
        val updated = search.withResult(
            MemoSearchUiResult.Success(query = "shopping", memos = listOf(memoFixture()))
        ) { mapped }

        // Assert
        assertEquals(mapped, updated.results)
    }

    @Test
    fun errorFailureForCurrentQueryMarksError() {
        // Arrange
        val search = SearchUiState().opened().withQuery("shopping")

        // Act
        // Error: failure keeps the query and reports the error to the screen.
        val updated = search.withResult(MemoSearchUiResult.Failure(query = "shopping")) {
            listOf(memoUiModel())
        }

        // Assert
        assertAll(
            { assertEquals("shopping", updated.query) },
            { assertEquals(true, updated.hasError) }
        )
    }

    @Test
    fun flowInitialResultIsInactive() = runTest {
        // Arrange
        val controls = MutableStateFlow(SearchUiState())

        // Act & Assert
        // Flow: an untouched search emits the inactive raw result.
        searchResults(controls).test {
            assertEquals(MemoSearchUiResult.Inactive, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun interactionBlankQuerySkipsRepository() = runTest {
        // Arrange
        val controls = MutableStateFlow(SearchUiState())
        val repository = memoRepository()

        // Act & Assert
        // Interaction/Boundary: blank queries emit empty success without repository access.
        searchResults(controls, repository).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("   ")
            awaitItem()
            verify(exactly = 0) { repository.observeActiveMemosBySearchQuery(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineNonBlankQueryDoesNotSearchBeforeDebounce() = runTest {
        // Arrange
        val controls = MutableStateFlow(SearchUiState())
        val repository = memoRepository()

        // Act & Assert
        // Coroutine/Interaction: non-blank queries wait for the complete debounce window.
        searchResults(controls, repository).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("shopping")
            advanceTimeBy(249.milliseconds)
            runCurrent()
            verify(exactly = 0) { repository.observeActiveMemosBySearchQuery(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineNonBlankQuerySearchesAtDebounceBoundary() = runTest {
        // Arrange
        val memo = memoFixture(id = "memo-1")
        val controls = MutableStateFlow(SearchUiState())

        // Act & Assert
        // Coroutine: the query is searched when the 250ms debounce completes.
        searchResults(controls, memoRepository { flowOf(listOf(memo)) }).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("shopping")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(
                MemoSearchUiResult.Success(query = "shopping", memos = listOf(memo)),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineLatestQueryCancelsPreviousSearch() = runTest {
        // Arrange
        val cancelledQueries = mutableListOf<String>()
        val repository = memoRepository { query ->
            if (query == "first") {
                flow {
                    try {
                        emit(listOf(memoFixture(id = "first")))
                        awaitCancellation()
                    } finally {
                        cancelledQueries += query
                    }
                }
            } else {
                flowOf(listOf(memoFixture(id = "second")))
            }
        }
        val controls = MutableStateFlow(SearchUiState())

        // Act & Assert
        // Coroutine: the latest debounced query cancels the previously collected search Flow.
        searchResults(controls, repository).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("first")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            awaitItem()
            controls.query("second")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            awaitItem()
            assertEquals(listOf("first"), cancelledQueries)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun normalEmptySearchEmitsSuccess() = runTest {
        // Arrange
        val controls = MutableStateFlow(SearchUiState())

        // Act & Assert
        // Normal: an empty repository result remains distinct from search failure.
        searchResults(controls, memoRepository { flowOf(emptyList()) }).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("missing")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(
                MemoSearchUiResult.Success(query = "missing", memos = emptyList()),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun errorSearchFailureEmitsFailure() = runTest {
        // Arrange
        val controls = MutableStateFlow(SearchUiState())
        val repository = memoRepository { flow { throw IllegalStateException("Search failed.") } }

        // Act & Assert
        // Error: repository failure has an explicit result distinct from empty success.
        searchResults(controls, repository).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("shopping")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(MemoSearchUiResult.Failure(query = "shopping"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineCloseCancelsPendingSearch() = runTest {
        // Arrange
        val controls = MutableStateFlow(SearchUiState())
        val repository = memoRepository()

        // Act & Assert
        // Coroutine/Interaction: close cancels a query still inside the debounce window.
        searchResults(controls, repository).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("shopping")
            advanceTimeBy(249.milliseconds)
            controls.close()
            runCurrent()
            awaitItem()
            advanceTimeBy(1.milliseconds)
            runCurrent()
            verify(exactly = 0) { repository.observeActiveMemosBySearchQuery(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineCancellationDoesNotEmitFailure() = runTest {
        // Arrange
        val repository = memoRepository { query ->
            if (query == "first") flow { awaitCancellation() } else flowOf(emptyList())
        }
        val controls = MutableStateFlow(SearchUiState())

        // Act & Assert
        // Coroutine/Error: flatMapLatest cancellation continues with the latest success.
        searchResults(controls, repository).test {
            awaitItem()
            controls.open()
            awaitItem()
            controls.query("first")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            controls.query("second")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(
                MemoSearchUiResult.Success(query = "second", memos = emptyList()),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun MutableStateFlow<SearchUiState>.open() = update { search -> search.opened() }

    private fun MutableStateFlow<SearchUiState>.close() = update { search -> search.closed() }

    private fun MutableStateFlow<SearchUiState>.query(query: String) =
        update { search -> search.withQuery(query) }

    private fun searchResults(
        controls: Flow<SearchUiState>,
        repository: MemoRepository = memoRepository()
    ): Flow<MemoSearchUiResult> = controls.searchMemoResults(
        SearchMemosUseCase(
            memoRepository = repository,
            displaySettingsRepository = FakeDisplaySettingsRepository()
        )
    )

    private fun memoRepository(
        search: (String) -> Flow<List<Memo>> = { flowOf(emptyList()) }
    ): MemoRepository = mockk<MemoRepository>().also { repository ->
        every { repository.observeActiveMemosBySearchQuery(any()) } answers {
            search(firstArg<String>())
        }
    }

    private fun memoUiModel() = MemoUiModel(
        id = MemoId("memo-1"),
        title = "Shopping",
        body = "Buy coffee",
        tags = emptyList(),
        updatedAtMillis = 1_000L,
        isFavorite = false
    )

}
