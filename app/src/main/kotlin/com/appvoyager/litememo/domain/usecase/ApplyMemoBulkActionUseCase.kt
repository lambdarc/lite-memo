package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.ApplyMemoBulkActionCommand
import com.appvoyager.litememo.domain.model.Memo
import com.appvoyager.litememo.domain.model.MemoBulkAction
import com.appvoyager.litememo.domain.model.MemoTrashUpdate
import com.appvoyager.litememo.domain.model.updatedAtFrom
import com.appvoyager.litememo.domain.model.value.TagId
import com.appvoyager.litememo.domain.provider.CurrentTimeProvider
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.repository.TagRepository
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
        val updated = memos
            .filter { it.isFavorite != isFavorite }
            .map { memo ->
                memo.copy(
                    updatedAt = memo.updatedAtFrom(now),
                    isFavorite = isFavorite
                )
            }
        memoRepository.saveAllActiveMemos(
            expectedActiveIds = memos.map { it.id },
            memos = updated
        )
    }

    private suspend fun addTag(memos: List<Memo>, tagId: TagId) {
        val now = currentTimeProvider.now()
        val updated = memos
            .filter { tagId !in it.tagIds }
            .map { memo ->
                memo.copy(
                    updatedAt = memo.updatedAtFrom(now),
                    tagIds = memo.tagIds + tagId
                )
            }
        memoRepository.saveAllActiveMemos(
            expectedActiveIds = memos.map { it.id },
            memos = updated
        )
    }

    private suspend fun removeTag(memos: List<Memo>, tagId: TagId) {
        val now = currentTimeProvider.now()
        val updated = memos
            .filter { tagId in it.tagIds }
            .map { memo ->
                memo.copy(
                    updatedAt = memo.updatedAtFrom(now),
                    tagIds = memo.tagIds.filterNot { it == tagId }
                )
            }
        memoRepository.saveAllActiveMemos(
            expectedActiveIds = memos.map { it.id },
            memos = updated
        )
    }

}
