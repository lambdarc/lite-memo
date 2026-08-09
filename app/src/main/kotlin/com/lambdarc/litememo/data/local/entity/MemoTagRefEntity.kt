package com.lambdarc.litememo.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "memo_tag_refs",
    primaryKeys = ["memoId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = MemoEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["memoId", "position"], unique = true), Index(value = ["tagId"])]
)
data class MemoTagRefEntity(val memoId: String, val tagId: String, val position: Int)
