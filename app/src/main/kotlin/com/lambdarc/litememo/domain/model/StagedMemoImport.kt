package com.lambdarc.litememo.domain.model

import com.lambdarc.litememo.domain.model.value.MemoImportSessionToken

data class StagedMemoImport(val token: MemoImportSessionToken, val data: ExportData)
