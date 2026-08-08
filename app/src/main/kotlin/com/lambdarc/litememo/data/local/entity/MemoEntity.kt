package com.lambdarc.litememo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memos",
    indices = [Index(value = ["createdAt"]), Index(value = ["deletedAt"])]
)
data class MemoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean,
    val deletedAt: Long?
)
