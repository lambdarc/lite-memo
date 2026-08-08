package com.lambdarc.litememo.ui.state

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TutorialUiStateTest {

    @Test
    fun stateTransitionShowsTutorialWhenIncompleteObservedFromLoading() {
        // Arrange
        val state = TutorialUiState(status = TutorialUiStatus.LOADING)

        // Act
        // StateTransition: incomplete flag from loading shows tutorial
        val result = state.next(completed = false)

        // Assert
        assertEquals(TutorialUiState(status = TutorialUiStatus.VISIBLE), result)
    }

    @Test
    fun stateTransitionHidesTutorialWhenCompletedObservedFromLoading() {
        // Arrange
        val state = TutorialUiState(status = TutorialUiStatus.LOADING)

        // Act
        // StateTransition: completed flag from loading hides tutorial
        val result = state.next(completed = true)

        // Assert
        assertEquals(TutorialUiState(status = TutorialUiStatus.HIDDEN), result)
    }

    @Test
    fun stateTransitionHidesTutorialWhenCompletedObservedFromVisible() {
        // Arrange
        val state = TutorialUiState(status = TutorialUiStatus.VISIBLE)

        // Act
        // StateTransition: completed flag from visible hides tutorial
        val result = state.next(completed = true)

        // Assert
        assertEquals(TutorialUiState(status = TutorialUiStatus.HIDDEN), result)
    }

    @Test
    fun stateTransitionKeepsVisibleWhenIncompleteObservedFromVisible() {
        // Arrange
        val state = TutorialUiState(status = TutorialUiStatus.VISIBLE)

        // Act
        // StateTransition: incomplete flag from visible keeps tutorial visible
        val result = state.next(completed = false)

        // Assert
        assertEquals(TutorialUiState(status = TutorialUiStatus.VISIBLE), result)
    }

    @Test
    fun stateTransitionKeepsHiddenWhenIncompleteIsObservedAfterHidden() {
        // Arrange
        val state = TutorialUiState(status = TutorialUiStatus.HIDDEN)

        // Act
        // StateTransition: false after hidden does not show tutorial again
        val result = state.next(completed = false)

        // Assert
        assertEquals(TutorialUiState(status = TutorialUiStatus.HIDDEN), result)
    }
}
