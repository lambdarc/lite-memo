package com.lambdarc.litememo.domain.repository

import com.lambdarc.litememo.domain.model.ExportData
import com.lambdarc.litememo.domain.model.value.ExportFileReference
import com.lambdarc.litememo.domain.model.value.MemoExportToken

interface MemoExportArchiveRepository {

    suspend fun prepare(data: ExportData): MemoExportToken

    suspend fun write(token: MemoExportToken, destination: ExportFileReference)

    suspend fun discard(token: MemoExportToken)

    suspend fun deleteAbandonedPreparedExports()

}
