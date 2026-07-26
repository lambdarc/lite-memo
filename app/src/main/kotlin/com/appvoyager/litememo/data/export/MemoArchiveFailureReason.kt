package com.appvoyager.litememo.data.export

internal enum class MemoArchiveFailureReason {
    UNSUPPORTED_VERSION,
    INVALID_SIGNATURE,
    MALFORMED_ARCHIVE,
    INVALID_ENTRY_NAME,
    DUPLICATE_IDENTIFIER,
    LIMIT_EXCEEDED,
    SIZE_MISMATCH,
    CHECKSUM_MISMATCH
}
