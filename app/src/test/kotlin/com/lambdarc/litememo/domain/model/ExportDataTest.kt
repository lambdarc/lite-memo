package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.TimestampMillis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExportDataTest {

    @Test
    fun boundaryConstructorAcceptsFirstVersion() {
        // Act
        // Boundary: version 1 is the smallest valid version
        val data = exportData(version = 1)

        // Assert
        assertEquals(1, data.version)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun errorConstructorThrowsWhenVersionIsNotPositive(version: Int) {
        // Act & Assert
        // Error: zero and negative versions are rejected
        assertThrows(IllegalArgumentException::class.java) { exportData(version = version) }
    }

    private fun exportData(version: Int) = ExportData(
        version = version,
        exportedAt = TimestampMillis(1000L),
        tags = emptyList(),
        memos = emptyList()
    )

}
