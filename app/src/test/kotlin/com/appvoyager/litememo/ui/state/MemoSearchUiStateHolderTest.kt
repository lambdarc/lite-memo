package com.appvoyager.litememo.ui.state

import app.cash.turbine.test
import com.appvoyager.litememo.domain.memoFixture
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.repository.FakeDisplaySettingsRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.usecase.SearchMemosUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class MemoSearchUiStateHolderTest {

    @Test
    fun stateTransitionInitialControlsAreInactive() {
        // Arrange
        val holder = stateHolder()

        // Act
        // StateTransition: a screen-specific holder starts with the inactive invariant.
        val controls = holder.controls.value

        // Assert
        assertEquals(SearchUiState(), controls)
    }

    @Test
    fun flowInitialResultIsInactive() = runTest {
        // Arrange
        val holder = stateHolder()

        // Act & Assert
        // Flow: a new holder emits the inactive raw result before any interaction.
        holder.results.test {
            assertEquals(MemoSearchUiResult.Inactive, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stateTransitionOpenActivatesBlankSearch() {
        // Arrange
        val holder = stateHolder()

        // Act
        // StateTransition: open activates a clean search snapshot.
        holder.open()

        // Assert
        assertEquals(SearchUiState(isActive = true), holder.controls.value)
    }

    @Test
    fun stateTransitionToggleClosesActiveSearch() {
        // Arrange
        val holder = stateHolder()
        holder.open()
        holder.updateQuery("shopping")

        // Act
        // StateTransition: toggling active search performs a complete reset.
        holder.toggle()

        // Assert
        assertEquals(SearchUiState(), holder.controls.value)
    }

    @Test
    fun stateTransitionCloseResetsActiveSearch() {
        // Arrange
        val holder = stateHolder()
        holder.open()
        holder.updateQuery("shopping")

        // Act
        // StateTransition: close restores the inactive invariant atomically.
        holder.close()

        // Assert
        assertEquals(SearchUiState(), holder.controls.value)
    }

    @Test
    fun boundaryInactiveUpdateQueryIsIgnored() {
        // Arrange
        val holder = stateHolder()

        // Act
        // Boundary: query input outside active search is a no-op.
        holder.updateQuery("shopping")

        // Assert
        assertEquals(SearchUiState(), holder.controls.value)
    }

    @Test
    fun interactionBlankQuerySkipsRepository() = runTest {
        // Arrange
        val repository = memoRepository()
        val holder = stateHolder(repository)

        // Act & Assert
        // Interaction/Boundary: blank queries emit empty success without repository access.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("   ")
            awaitItem()
            verify(exactly = 0) { repository.observeActiveMemosBySearchQuery(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineNonBlankQueryDoesNotSearchBeforeDebounce() = runTest {
        // Arrange
        val repository = memoRepository()
        val holder = stateHolder(repository)

        // Act & Assert
        // Coroutine/Interaction: non-blank queries wait for the complete debounce window.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("shopping")
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
        val holder = stateHolder(memoRepository { flowOf(listOf(memo)) })

        // Act & Assert
        // Coroutine: the query is searched when the 250ms debounce completes.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("shopping")
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
        val holder = stateHolder(repository)

        // Act & Assert
        // Coroutine: the latest debounced query cancels the previously collected search Flow.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("first")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            awaitItem()
            holder.updateQuery("second")
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
        val holder = stateHolder(memoRepository { flowOf(emptyList()) })

        // Act & Assert
        // Normal: an empty repository result remains distinct from search failure.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("missing")
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
        val holder = stateHolder(
            memoRepository { flow { throw IllegalStateException("Search failed.") } }
        )

        // Act & Assert
        // Error: repository failure has an explicit result distinct from empty success.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("shopping")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(MemoSearchUiResult.Failure(query = "shopping"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun stateTransitionFailurePreservesQuery() = runTest {
        // Arrange
        val holder = stateHolder(
            memoRepository { flow { throw IllegalStateException("Search failed.") } }
        )

        // Act & Assert
        // StateTransition/Error: failure does not clear the user's active query.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("shopping")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            val result = awaitItem()
            val uiState = holder.toUiState(holder.controls.value, result) { emptyList() }
            assertEquals("shopping", uiState.query)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun coroutineCloseCancelsPendingSearch() = runTest {
        // Arrange
        val repository = memoRepository()
        val holder = stateHolder(repository)

        // Act & Assert
        // Coroutine/Interaction: close cancels a query still inside the debounce window.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("shopping")
            advanceTimeBy(249.milliseconds)
            holder.close()
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
            if (query == "first") {
                flow { awaitCancellation() }
            } else {
                flowOf(emptyList())
            }
        }
        val holder = stateHolder(repository)

        // Act & Assert
        // Coroutine/Error: flatMapLatest cancellation continues with the latest success.
        holder.results.test {
            awaitItem()
            holder.open()
            awaitItem()
            holder.updateQuery("first")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            holder.updateQuery("second")
            advanceTimeBy(250.milliseconds)
            runCurrent()
            assertEquals(
                MemoSearchUiResult.Success(query = "second", memos = emptyList()),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun stateHolder(repository: MemoRepository = memoRepository()) =
        MemoSearchUiStateHolder(
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
}
