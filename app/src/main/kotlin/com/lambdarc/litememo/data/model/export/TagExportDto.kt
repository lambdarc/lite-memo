package com.lambdarc.litememo.data.model.export

import kotlinx.serialization.Serializable

@Serializable
data class TagExportDto(val id: String, val name: String, val colorArgb: Long, val createdAt: Long)
