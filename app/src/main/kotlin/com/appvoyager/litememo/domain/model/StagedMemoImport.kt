package com.appvoyager.litememo.domain.model

import com.appvoyager.litememo.domain.model.value.MemoImportSessionToken

data class StagedMemoImport(val token: MemoImportSessionToken, val data: ExportData)
