package com.lambdarc.litememo.ui.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MonthSwipeTest {

    @Test
    fun resolvesToNextWhenDraggedLeftBeyondThreshold() {
        // Act
        val result = resolveMonthSwipe(dragAmount = -120f, thresholdPx = 100f)

        // Assert
        assertEquals(MonthSwipeUiDirection.NEXT, result)
    }

    @Test
    fun resolvesToPreviousWhenDraggedRightBeyondThreshold() {
        // Act
        val result = resolveMonthSwipe(dragAmount = 120f, thresholdPx = 100f)

        // Assert
        assertEquals(MonthSwipeUiDirection.PREVIOUS, result)
    }

    @Test
    fun resolvesToNullWhenDragIsBelowThreshold() {
        // Act
        val result = resolveMonthSwipe(dragAmount = -80f, thresholdPx = 100f)

        // Assert
        assertNull(result)
    }

    @Test
    fun resolvesToNullWhenDragIsZero() {
        // Act
        val result = resolveMonthSwipe(dragAmount = 0f, thresholdPx = 100f)

        // Assert
        assertNull(result)
    }

    @Test
    fun resolvesToNextWhenDragEqualsNegativeThreshold() {
        // Act
        val result = resolveMonthSwipe(dragAmount = -100f, thresholdPx = 100f)

        // Assert
        assertEquals(MonthSwipeUiDirection.NEXT, result)
    }

    @Test
    fun resolvesToPreviousWhenDragEqualsPositiveThreshold() {
        // Act
        val result = resolveMonthSwipe(dragAmount = 100f, thresholdPx = 100f)

        // Assert
        assertEquals(MonthSwipeUiDirection.PREVIOUS, result)
    }
}
