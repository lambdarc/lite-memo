package com.appvoyager.litememo.domain.exception

enum class MemoImportFailureReason {
    UNSUPPORTED_VERSION,
    INVALID_ARCHIVE,
    INVALID_IMAGE,
    SIZE_LIMIT_EXCEEDED,
    INSUFFICIENT_STORAGE
}

class MemoImportException(
    val reason: MemoImportFailureReason,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
