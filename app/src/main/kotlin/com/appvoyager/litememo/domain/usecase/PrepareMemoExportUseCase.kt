package com.appvoyager.litememo.domain.usecase

import com.appvoyager.litememo.domain.model.value.MemoExportToken
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
import javax.inject.Inject

class PrepareMemoExportUseCase @Inject constructor(
    private val exportMemosUseCase: ExportMemosUseCase,
    private val memoExportArchiveRepository: MemoExportArchiveRepository
) {

    suspend operator fun invoke(): MemoExportToken =
        memoExportArchiveRepository.prepare(exportMemosUseCase())

}
