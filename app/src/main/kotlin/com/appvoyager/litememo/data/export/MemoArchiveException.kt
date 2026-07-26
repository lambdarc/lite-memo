package com.appvoyager.litememo.data.export

import java.io.IOException

internal class MemoArchiveException(
    val reason: MemoArchiveFailureReason,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

internal fun archiveFailure(
    reason: MemoArchiveFailureReason,
    message: String,
    cause: Throwable? = null
): Nothing = throw MemoArchiveException(reason, message, cause)
