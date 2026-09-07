package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.repository.MemoExportArchiveRepository
import javax.inject.Inject

class DeleteAbandonedPreparedExportsUseCase @Inject constructor(
    private val memoExportArchiveRepository: MemoExportArchiveRepository
) {

    suspend operator fun invoke() = memoExportArchiveRepository.deleteAbandonedPreparedExports()

}
