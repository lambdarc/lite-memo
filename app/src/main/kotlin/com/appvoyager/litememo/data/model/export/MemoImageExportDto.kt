package com.appvoyager.litememo.data.model.export

import kotlinx.serialization.Serializable

@Serializable
data class MemoImageExportDto(
    val id: String,
    val fileName: String,
    val archiveEntry: String,
    val sizeBytes: Long,
    val sha256: String
)
