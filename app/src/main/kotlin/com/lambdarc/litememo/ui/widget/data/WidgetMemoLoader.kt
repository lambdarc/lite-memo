package com.lambdarc.litememo.ui.widget.data

import com.lambdarc.litememo.domain.model.MemoSummary
import com.lambdarc.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.lambdarc.litememo.domain.usecase.ObserveRecentMemosUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class WidgetMemoLoader(
    private val observeRecentMemosUseCase: ObserveRecentMemosUseCase,
    private val observeAppLockEnabledUseCase: ObserveAppLockEnabledUseCase
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeRecent(): Flow<List<WidgetItem>> = observeAppLockEnabledUseCase()
        .catch { error ->
            if (error is CancellationException) throw error
            emit(true)
        }
        .distinctUntilChanged()
        .flatMapLatest { appLockEnabled ->
            if (appLockEnabled) {
                flowOf(emptyList())
            } else {
                observeRecentMemosUseCase(RECENT_MEMOS_LIMIT)
                    .map { memos -> memos.map { it.toWidgetItem() } }
            }
        }

    suspend fun loadRecent(): List<WidgetItem> = observeRecent().first()

}

internal const val RECENT_MEMOS_LIMIT = 8
private const val MAX_TITLE_LENGTH = 50
private const val MAX_SNIPPET_LENGTH = 80

private const val BODY_SCAN_PREFIX = (MAX_TITLE_LENGTH + MAX_SNIPPET_LENGTH) * 4

private fun MemoSummary.toWidgetItem(): WidgetItem {
    val trimmedTitle = title.value.trim()
    val bodyLines = body.value.trimStart().take(BODY_SCAN_PREFIX).trim().lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val primary: String
    val snippet: String
    if (trimmedTitle.isNotEmpty()) {
        primary = trimmedTitle.takeCodePointSafe(MAX_TITLE_LENGTH)
        snippet = bodyLines.joinToString(" ")
    } else {
        val firstLine = bodyLines.firstOrNull().orEmpty()
        primary = firstLine.takeCodePointSafe(MAX_TITLE_LENGTH)
        val firstLineRemainder = firstLine.removePrefix(primary).trim()
        snippet = (listOf(firstLineRemainder) + bodyLines.drop(1))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
    }
    return WidgetItem(
        id = id,
        title = primary.takeCodePointSafe(MAX_TITLE_LENGTH),
        snippet = snippet.takeCodePointSafe(MAX_SNIPPET_LENGTH),
        isFavorite = isFavorite
    )
}

private fun String.takeCodePointSafe(maxChars: Int): String {
    if (maxChars <= 0) return ""
    if (length <= maxChars) return this
    val lastIndex = maxChars - 1
    val end = if (this[lastIndex].isHighSurrogate()) lastIndex else maxChars
    return substring(0, end)
}
