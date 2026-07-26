package com.appvoyager.litememo.domain.repository

import com.appvoyager.litememo.domain.model.ExportData
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoExportToken

interface MemoExportArchiveRepository {

    suspend fun prepare(data: ExportData): MemoExportToken

    suspend fun write(token: MemoExportToken, destination: ExportFileReference)

    suspend fun discard(token: MemoExportToken)

    suspend fun deleteAbandonedPreparedExports()

}
