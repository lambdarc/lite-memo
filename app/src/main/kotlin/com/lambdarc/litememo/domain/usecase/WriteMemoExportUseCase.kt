package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.ExportFileReference
import com.lambdarc.litememo.domain.model.value.MemoExportToken
import com.lambdarc.litememo.domain.repository.MemoExportArchiveRepository
import javax.inject.Inject

class WriteMemoExportUseCase @Inject constructor(
    private val memoExportArchiveRepository: MemoExportArchiveRepository
) {

    suspend operator fun invoke(token: MemoExportToken, destination: ExportFileReference) =
        memoExportArchiveRepository.write(token, destination)

}
