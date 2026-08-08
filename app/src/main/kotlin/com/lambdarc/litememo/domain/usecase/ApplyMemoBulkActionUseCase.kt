package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.ActiveMemoBulkWrite
import com.lambdarc.litememo.domain.model.ApplyMemoBulkActionCommand
import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.MemoBulkAction
import com.lambdarc.litememo.domain.model.MemoTrashUpdate
import com.lambdarc.litememo.domain.model.updatedAtFrom
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import javax.inject.Inject

class ApplyMemoBulkActionUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val tagRepository: TagRepository,
    private val currentTimeProvider: CurrentTimeProvider
) {

    suspend operator fun invoke(command: ApplyMemoBulkActionCommand) {
        val memoIds = command.memoIds.distinct()
        if (memoIds.isEmpty()) return

        val memoById = memoRepository.getActiveMemos(memoIds).associateBy { it.id }
        val memos = memoIds.map { id ->
            requireNotNull(memoById[id]) {
                "Memo not found: ${id.value}"
            }
        }
        val tagId = command.action.tagIdOrNull()
        if (tagId != null) {
            requireNotNull(tagRepository.getTag(tagId)) {
                "Tag not found: ${tagId.value}"
            }
        }

        applyAction(memos, command.action)
    }

    private suspend fun applyAction(memos: List<Memo>, action: MemoBulkAction) {
        when (action) {
            MemoBulkAction.MoveToTrash -> moveToTrash(memos)

            is MemoBulkAction.SetFavorite -> setFavorite(
                memos = memos,
                isFavorite = action.isFavorite
            )

            is MemoBulkAction.AddTag -> addTag(
                memos = memos,
                tagId = action.tagId
            )

            is MemoBulkAction.RemoveTag -> removeTag(
                memos = memos,
                tagId = action.tagId
            )
        }
    }

    private fun MemoBulkAction.tagIdOrNull(): TagId? = when (this) {
        is MemoBulkAction.AddTag -> tagId

        is MemoBulkAction.RemoveTag -> tagId

        MemoBulkAction.MoveToTrash,
        is MemoBulkAction.SetFavorite -> null
    }

    private suspend fun moveToTrash(memos: List<Memo>) {
        val now = currentTimeProvider.now()
        memoRepository.moveMemosToTrash(
            memos.map { memo ->
                MemoTrashUpdate(
                    memoId = memo.id,
                    deletedAt = memo.updatedAtFrom(now)
                )
            }
        )
    }

    private suspend fun setFavorite(memos: List<Memo>, isFavorite: Boolean) {
        val now = currentTimeProvider.now()
        saveActiveMemoBulkWrites(memos) { memo ->
            if (memo.isFavorite == isFavorite) {
                null
            } else {
                memo.copy(
                    updatedAt = memo.updatedAtFrom(now),
                    isFavorite = isFavorite
                )
            }
        }
    }

    private suspend fun addTag(memos: List<Memo>, tagId: TagId) {
        val now = currentTimeProvider.now()
        saveActiveMemoBulkWrites(memos) { memo ->
            if (tagId in memo.tagIds) {
                null
            } else {
                memo.copy(
                    updatedAt = memo.updatedAtFrom(now),
                    tagIds = memo.tagIds + tagId
                )
            }
        }
    }

    private suspend fun removeTag(memos: List<Memo>, tagId: TagId) {
        val now = currentTimeProvider.now()
        saveActiveMemoBulkWrites(memos) { memo ->
            if (tagId !in memo.tagIds) {
                null
            } else {
                memo.copy(
                    updatedAt = memo.updatedAtFrom(now),
                    tagIds = memo.tagIds.filterNot { it == tagId }
                )
            }
        }
    }

    private suspend fun saveActiveMemoBulkWrites(
        memos: List<Memo>,
        updatedMemoOrNullIfUnchanged: (Memo) -> Memo?
    ) {
        val writes = memos.map { memo ->
            val updatedMemo = updatedMemoOrNullIfUnchanged(memo)
            if (updatedMemo == null) {
                ActiveMemoBulkWrite.CheckOnly(
                    memoId = memo.id,
                    expectedUpdatedAt = memo.updatedAt
                )
            } else {
                ActiveMemoBulkWrite.Update(
                    memoId = memo.id,
                    expectedUpdatedAt = memo.updatedAt,
                    updatedMemo = updatedMemo
                )
            }
        }
        memoRepository.saveActiveMemoBulkWrites(writes)
    }

}
