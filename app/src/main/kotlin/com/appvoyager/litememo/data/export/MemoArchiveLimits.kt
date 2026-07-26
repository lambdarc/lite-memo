package com.appvoyager.litememo.data.export

data class MemoArchiveLimits(
    val maxArchiveBytes: Long,
    val maxManifestBytes: Long,
    val maxImageBytes: Long,
    val maxTotalImageBytes: Long,
    val maxEntryCount: Int
) {

    companion object {
        val DEFAULT = MemoArchiveLimits(
            maxArchiveBytes = DEFAULT_MAX_ARCHIVE_BYTES,
            maxManifestBytes = DEFAULT_MAX_MANIFEST_BYTES,
            maxImageBytes = DEFAULT_MAX_IMAGE_BYTES,
            maxTotalImageBytes = DEFAULT_MAX_TOTAL_IMAGE_BYTES,
            maxEntryCount = DEFAULT_MAX_ENTRY_COUNT
        )
    }

}

private const val BYTES_PER_MIB = 1024L * 1024

private const val DEFAULT_MAX_ARCHIVE_BYTES = 320L * BYTES_PER_MIB

private const val DEFAULT_MAX_MANIFEST_BYTES = 5L * BYTES_PER_MIB

private const val DEFAULT_MAX_IMAGE_BYTES = 32L * BYTES_PER_MIB

private const val DEFAULT_MAX_TOTAL_IMAGE_BYTES = 256L * BYTES_PER_MIB

private const val DEFAULT_MAX_ENTRY_COUNT = 1_001
