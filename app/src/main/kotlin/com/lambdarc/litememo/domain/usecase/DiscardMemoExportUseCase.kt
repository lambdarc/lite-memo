package com.lambdarc.litememo.domain.usecase

import com.lambdarc.litememo.domain.model.value.MemoExportToken
import com.lambdarc.litememo.domain.repository.MemoExportArchiveRepository
import javax.inject.Inject

class DiscardMemoExportUseCase @Inject constructor(
    private val memoExportArchiveRepository: MemoExportArchiveRepository
) {

    suspend operator fun invoke(token: MemoExportToken) = memoExportArchiveRepository.discard(token)

}
