package com.lambdarc.litememo.ui.state

import com.lambdarc.litememo.domain.model.value.MemoId
import org.junit.jupiter.api.Assertions.assertAll
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
        assertAll(
            { assertEquals(emptySet<MemoId>(), selection.selectedMemoIds) },
            { assertFalse(selection.isActive) },
            { assertEquals(0, selection.selectedCount) }
        )
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
    fun normalContainsReturnsTrueForSelectedMemoId() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // Normal: contains reports a selected id
        val contains = selection.contains(memoId("memo-1"))

        // Assert
        assertTrue(contains)
    }

    @Test
    fun normalContainsReturnsFalseForUnselectedMemoId() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // Normal: contains rejects an unselected id
        val contains = selection.contains(memoId("memo-2"))

        // Assert
        assertFalse(contains)
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
    fun stateTransitionSelectOnlyKeepsSourceStateUnchanged() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // StateTransition: selectOnly returns a new state without mutating the source
        selection.selectOnly(memoId("memo-2"))

        // Assert
        assertEquals(setOf(memoId("memo-1")), selection.selectedMemoIds)
    }

    @Test
    fun stateTransitionToggleKeepsSourceStateUnchanged() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // StateTransition: toggle returns a new state without mutating the source
        selection.toggle(memoId("memo-2"))

        // Assert
        assertEquals(setOf(memoId("memo-1")), selection.selectedMemoIds)
    }

    @Test
    fun stateTransitionClearKeepsSourceStateUnchanged() {
        // Arrange
        val selection = MemoSelectionUiState(setOf(memoId("memo-1")))

        // Act
        // StateTransition: clear returns a new state without mutating the source
        selection.clear()

        // Assert
        assertEquals(setOf(memoId("memo-1")), selection.selectedMemoIds)
    }

    private fun memoId(value: String) = MemoId(value)

}
