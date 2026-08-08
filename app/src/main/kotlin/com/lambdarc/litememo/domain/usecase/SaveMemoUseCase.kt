package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.Memo
import com.lambdarc.litememo.domain.model.SaveMemoCommand
import com.lambdarc.litememo.domain.model.updatedAtFrom
import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.model.value.TimestampMillis
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.provider.MemoIdProvider
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import javax.inject.Inject

class SaveMemoUseCase @Inject constructor(
    private val memoRepository: MemoRepository,
    private val tagRepository: TagRepository,
    private val memoIdProvider: MemoIdProvider,
    private val currentTimeProvider: CurrentTimeProvider
) {

    suspend operator fun invoke(command: SaveMemoCommand): Memo {
        require(
            command.title.value.isNotBlank() ||
                command.body.value.isNotBlank() ||
                command.images.isNotEmpty()
        ) {
            "Memo title, body, or images must not be empty."
        }
        val now = currentTimeProvider.now()
        val existingMemo = command.id?.let { id -> memoRepository.getActiveMemo(id) }
        val tagIds = command.tagIds.distinct()
        val images = command.images.distinctBy { it.id }
        validateTagIds(tagIds)
        val createdAt = existingMemo?.createdAt ?: command.createdAt ?: now
        val updatedAt = existingMemo?.updatedAtFrom(now)
            ?: TimestampMillis(maxOf(now.value, createdAt.value))
        val memo = Memo(
            id = command.id ?: memoIdProvider.newMemoId(),
            title = command.title,
            body = command.body,
            createdAt = createdAt,
            updatedAt = updatedAt,
            tagIds = tagIds,
            images = images,
            isFavorite = command.isFavorite
        )

        memoRepository.saveMemo(memo)
        return memo
    }

    private suspend fun validateTagIds(tagIds: List<TagId>) {
        if (tagIds.isEmpty()) return
        val existingIds = tagRepository.getTagsByIds(tagIds).map { it.id }.toSet()
        val missingIds = tagIds.filterNot { it in existingIds }
        require(missingIds.isEmpty()) {
            "Memo references tags that do not exist: ${missingIds.joinToString { it.value }}."
        }
    }

}
