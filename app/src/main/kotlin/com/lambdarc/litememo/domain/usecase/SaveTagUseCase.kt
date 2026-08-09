package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.exception.DuplicateTagNameException
import com.lambdarc.litememo.domain.model.SaveTagCommand
import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.provider.TagIdProvider
import com.lambdarc.litememo.domain.repository.TagRepository
import javax.inject.Inject

class SaveTagUseCase @Inject constructor(
    private val tagRepository: TagRepository,
    private val tagIdProvider: TagIdProvider,
    private val currentTimeProvider: CurrentTimeProvider
) {

    suspend operator fun invoke(command: SaveTagCommand): Tag {
        val existingTag = command.id?.let { id ->
            requireNotNull(tagRepository.getTag(id)) { "Tag not found: ${id.value}" }
        }
        val duplicatedTag = tagRepository.findTagByName(command.name)
            ?.takeIf { it.id != command.id }
        if (duplicatedTag != null) throw DuplicateTagNameException(command.name)
        val tag = Tag(
            id = existingTag?.id ?: tagIdProvider.newTagId(),
            name = command.name,
            color = command.color,
            createdAt = existingTag?.createdAt ?: currentTimeProvider.now()
        )

        tagRepository.saveTag(tag)
        return tag
    }

}
