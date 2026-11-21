package com.lambdarc.litememo.domain.model.value

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class MemoImportSessionTokenTest {

    @Test
    fun normalConstructorPreservesSessionIdentifier() {
        // Act
        // Normal: a session identifier may contain dots without being a relative path.
        val token = MemoImportSessionToken("import.session-1")

        // Assert
        assertEquals("import.session-1", token.value)
    }

    @Test
    fun boundaryConstructorTrimsSurroundingWhitespace() {
        // Act
        // Boundary: surrounding whitespace is removed before validating the identifier.
        val token = MemoImportSessionToken(" \t session-1 \r\n")

        // Assert
        assertEquals("session-1", token.value)
    }

    @TestFactory
    fun boundaryConstructorRejectsUnsafeSessionIdentifiers() = mapOf(
        "empty" to "",
        "whitespace" to " \t\r\n",
        "forward separator" to "session/file",
        "backward separator" to "session\\file",
        "current directory" to ".",
        "parent directory" to "..",
        "padded parent directory" to " .. ",
        "embedded null" to "session\u0000id",
        "embedded newline" to "session\nid",
        "embedded tab" to "session\tid",
        "delete control" to "session\u007fid",
        "c1 control" to "session\u0085id"
    ).map { (description, input) ->
        dynamicTest(description) {
            // Act & Assert
            // Boundary: unsafe identifiers cannot become import-session paths.
            assertThrows(IllegalArgumentException::class.java) {
                MemoImportSessionToken(input)
            }
        }
    }
}
