package com.appvoyager.litememo.data.export

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemoArchiveLimitsTest {

    @Test
    fun normalDefaultLimitsRecordArchiveSafetyBudget() {
        // Act
        // Normal: the defaults are safety constants rather than a product specification
        val limits = MemoArchiveLimits.DEFAULT

        // Assert
        assertEquals(
            MemoArchiveLimits(
                maxArchiveBytes = 320L * BYTES_PER_MIB,
                maxManifestBytes = 5L * BYTES_PER_MIB,
                maxImageBytes = 32L * BYTES_PER_MIB,
                maxTotalImageBytes = 256L * BYTES_PER_MIB,
                maxEntryCount = 1_001
            ),
            limits
        )
    }

    @Test
    fun boundaryDefaultArchiveLimitAcceptsLargestExportableArchive() {
        // Arrange
        val limits = MemoArchiveLimits.DEFAULT

        // Act
        // Boundary: the largest archive this app can export stays within the import limit
        val largestExportableBytes = limits.maxManifestBytes + limits.maxTotalImageBytes

        // Assert
        assertTrue(limits.maxArchiveBytes > largestExportableBytes)
    }
}

private const val BYTES_PER_MIB = 1024L * 1024
