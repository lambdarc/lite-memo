package com.lambdarc.litememo.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["createdAt", "id"]),
        Index(value = ["name"], unique = true)
    ]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Long,
    val createdAt: Long
)
