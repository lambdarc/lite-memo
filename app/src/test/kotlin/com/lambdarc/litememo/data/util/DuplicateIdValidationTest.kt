package com.lambdarc.litememo.data.util

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DuplicateIdValidationTest {

    @Test
    fun boundaryEmptyListIsAccepted() {
        // Act & Assert
        // Boundary: nothing to compare means nothing is duplicated
        assertDoesNotThrow { emptyList<String>().requireNoDuplicateIds(label = "memo") { it } }
    }

    @Test
    fun normalDistinctIdsAreAccepted() {
        // Act & Assert
        // Normal: unique ids pass validation
        assertDoesNotThrow {
            listOf("a", "b", "c").requireNoDuplicateIds(label = "memo") { it }
        }
    }

    @Test
    fun errorSingleDuplicatedIdIsRejected() {
        // Act
        // Error: one repeated id is enough to reject the whole batch
        val actual = assertThrows(IllegalArgumentException::class.java) {
            listOf("a", "b", "a").requireNoDuplicateIds(label = "memo") { it }
        }

        // Assert
        assertEquals("Duplicate memo ids: [a]", actual.message)
    }

    @Test
    fun errorEveryDuplicatedIdIsReportedInMessage() {
        // Act
        // Error: all duplicated ids are reported, each once, in first-seen order
        val actual = assertThrows(IllegalArgumentException::class.java) {
            listOf("b", "a", "b", "c", "a", "a").requireNoDuplicateIds(label = "tag") { it }
        }

        // Assert
        assertEquals("Duplicate tag ids: [b, a]", actual.message)
    }

    @Test
    fun boundaryIdsAreComparedBySelectorResult() {
        // Act
        // Boundary: distinct elements sharing a selected id are duplicates
        val actual = assertThrows(IllegalArgumentException::class.java) {
            listOf("memo-1:old", "memo-1:new").requireNoDuplicateIds(label = "memo") {
                it.substringBefore(':')
            }
        }

        // Assert
        assertEquals("Duplicate memo ids: [memo-1]", actual.message)
    }

}
