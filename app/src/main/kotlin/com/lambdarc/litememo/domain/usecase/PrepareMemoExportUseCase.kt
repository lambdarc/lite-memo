package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.MemoExportToken
import com.lambdarc.litememo.domain.repository.MemoExportArchiveRepository
import javax.inject.Inject

class PrepareMemoExportUseCase @Inject constructor(
    private val exportMemosUseCase: ExportMemosUseCase,
    private val memoExportArchiveRepository: MemoExportArchiveRepository
) {

    suspend operator fun invoke(): MemoExportToken =
        memoExportArchiveRepository.prepare(exportMemosUseCase())

}
