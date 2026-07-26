package com.appvoyager.litememo.domain.repository

import com.appvoyager.litememo.domain.model.StagedMemoImport
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken

interface MemoImportArchiveRepository {

    suspend fun stageImportImages(reference: ExportFileReference): StagedMemoImport

    suspend fun completeStagedImport(token: MemoImportSessionToken)

    suspend fun rollbackStagedImport(token: MemoImportSessionToken)

    suspend fun deleteUnreferencedImportImages()

}
