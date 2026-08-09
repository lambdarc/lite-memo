package com.lambdarc.litememo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memo_images",
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["memoId", "position"], unique = true)]
)
data class MemoImageEntity(
    @PrimaryKey val id: String,
    val memoId: String,
    val fileName: String,
    val position: Int
)
