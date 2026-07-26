package com.appvoyager.litememo.ui.state

import com.appvoyager.litememo.domain.model.value.MemoId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoSelectionUiStateTest {

    @Test
    fun boundaryDefaultConstructorIsInactiveAndEmpty() {
        // Act
        // Boundary: default selection holds nothing and is inactive
        val selection = MemoSelectionUiState()

        // Assert
        assertEquals(emptySet<MemoId>(), selection.selectedMemoIds)
        assertFalse(selection.isActive)
        assertEquals(0, selection.selectedCount)
    }

    @Test
    fun normalSelectOnlyKeepsSingleMemoId() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1"), memoId("memo-2")))

        // Act
        // Normal: selectOnly replaces the whole selection
        val selected = selection.selectOnly(memoId("memo-3"))

        // Assert
        assertEquals(setOf(memoId("memo-3")), selected.selectedMemoIds)
    }

    @Test
    fun normalToggleAddsUnselectedMemoId() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // Normal: toggling an unselected id adds it
        val toggled = selection.toggle(memoId("memo-2"))

        // Assert
        assertEquals(setOf(memoId("memo-1"), memoId("memo-2")), toggled.selectedMemoIds)
    }

    @Test
    fun normalToggleRemovesSelectedMemoId() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1"), memoId("memo-2")))

        // Act
        // Normal: toggling a selected id removes it
        val toggled = selection.toggle(memoId("memo-1"))

        // Assert
        assertEquals(setOf(memoId("memo-2")), toggled.selectedMemoIds)
    }

    @Test
    fun boundaryToggleLastSelectedMemoIdBecomesInactive() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // Boundary: removing the last id turns the selection inactive
        val toggled = selection.toggle(memoId("memo-1"))

        // Assert
        assertFalse(toggled.isActive)
    }

    @Test
    fun normalContainsReportsSelectedMemoId() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // Normal: contains distinguishes selected ids from unselected ones
        val containsSelected = selection.contains(memoId("memo-1"))
        val containsOther = selection.contains(memoId("memo-2"))

        // Assert
        assertTrue(containsSelected)
        assertFalse(containsOther)
    }

    @Test
    fun normalClearReturnsEmptySelection() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1"), memoId("memo-2")))

        // Act
        // Normal: clear drops every selected id
        val cleared = selection.clear()

        // Assert
        assertEquals(MemoSelectionUiState(), cleared)
    }

    @Test
    fun stateTransitionTransitionsKeepSourceStateUnchanged() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // StateTransition: every transition returns a new state without mutating the source
        selection.selectOnly(memoId("memo-2"))
        selection.toggle(memoId("memo-2"))
        selection.clear()

        // Assert
        assertEquals(setOf(memoId("memo-1")), selection.selectedMemoIds)
    }

    private fun memoId(value: String) = MemoId(value)

}
