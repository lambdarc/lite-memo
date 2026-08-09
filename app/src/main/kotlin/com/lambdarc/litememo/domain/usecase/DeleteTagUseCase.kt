package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.TagId
import com.lambdarc.litememo.domain.repository.TagRepository
import javax.inject.Inject

class DeleteTagUseCase @Inject constructor(private val tagRepository: TagRepository) {

    suspend operator fun invoke(id: TagId) = tagRepository.deleteTag(id)

}
