package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.repository.MemoImportArchiveRepository
import javax.inject.Inject

class DeleteUnreferencedImportImagesUseCase @Inject constructor(
    private val memoImportArchiveRepository: MemoImportArchiveRepository
) {

    suspend operator fun invoke() = memoImportArchiveRepository.deleteUnreferencedImportImages()

}
