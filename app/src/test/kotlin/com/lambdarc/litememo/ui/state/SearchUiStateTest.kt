package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId
import com.lambdarc.litememo.ui.model.MemoUiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

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
        val search = SearchUiState().opened().updateQuery("shopping")

        // Act
        // StateTransition: toggling active search performs a complete reset.
        val toggled = search.toggled()

        // Assert
        assertEquals(SearchUiState(), toggled)
    }

    @Test
    fun stateTransitionCloseResetsActiveSearch() {
        // Arrange
        val search = SearchUiState().opened().updateQuery("shopping")

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
        val updated = search.updateQuery("shopping")

        // Assert
        assertEquals(SearchUiState(), updated)
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
