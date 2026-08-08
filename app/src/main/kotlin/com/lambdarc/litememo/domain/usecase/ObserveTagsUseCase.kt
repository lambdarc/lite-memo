package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.Tag
import com.lambdarc.litememo.domain.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTagsUseCase @Inject constructor(private val tagRepository: TagRepository) {

    operator fun invoke(): Flow<List<Tag>> = tagRepository.observeTags()

}
