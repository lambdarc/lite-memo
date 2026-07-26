package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.exception.ImportTagNameConflictException
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.Tag
import com.appvoyager.litememo.domain.model.value.TagName
import com.appvoyager.litememo.domain.repository.MemoImportRepository
import javax.inject.Inject

class ImportMemosUseCase @Inject constructor(
    private val memoImportRepository: MemoImportRepository
) {

    suspend operator fun invoke(data: ExportData) {
        if (data.version != ExportMemosUseCase.CURRENT_VERSION) {
            throw MemoImportException(
                MemoImportFailureReason.UNSUPPORTED_VERSION,
                "Unsupported export version: ${data.version}."
            )
        }

        data.memos.requireImportableContent()

        val conflictingTagNames = data.tags.conflictingTagNames()
        if (conflictingTagNames.isNotEmpty()) {
            throw ImportTagNameConflictException(conflictingTagNames)
        }

        val validTagIds = data.tags.map { it.id }.toSet()
        val sanitizedMemos = data.memos.map { memo ->
            val filtered = memo.tagIds.filter { it in validTagIds }.distinct()
            if (filtered.size == memo.tagIds.size) memo else memo.copy(tagIds = filtered)
        }
        memoImportRepository.import(data.copy(memos = sanitizedMemos))
    }

}

private fun List<Memo>.requireImportableContent() {
    if (any { it.title.value.isBlank() && it.body.value.isBlank() && it.images.isEmpty() }) {
        throw MemoImportException(
            MemoImportFailureReason.INVALID_ARCHIVE,
            "Import data contains a memo without title, body and images."
        )
    }

    val imageIds = flatMap { memo -> memo.images.map { it.id } }
    if (imageIds.distinct().size != imageIds.size) {
        throw MemoImportException(
            MemoImportFailureReason.INVALID_ARCHIVE,
            "Import data contains duplicated image ids."
        )
    }
}

private fun List<Tag>.conflictingTagNames(): List<TagName> = groupBy { it.name }
    .filterValues { tags -> tags.distinctBy { it.id }.size > 1 }
    .keys
    .sortedBy { it.value }
