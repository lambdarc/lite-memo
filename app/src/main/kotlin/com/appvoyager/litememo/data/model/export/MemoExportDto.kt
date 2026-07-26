package com.appvoyager.litememo.data.model.export

import kotlinx.serialization.Serializable

@Serializable
data class MemoExportDto(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val tagIds: List<String> = emptyList(),
    val images: List<MemoImageExportDto> = emptyList()
)
