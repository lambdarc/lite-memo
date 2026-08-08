package com.lambdarc.litememo.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.lambdarc.litememo.data.local.entity.MemoEntity
import com.lambdarc.litememo.data.local.entity.MemoImageEntity
import com.lambdarc.litememo.data.local.entity.MemoTagRefEntity

data class MemoWithRefs(
    @Embedded val memo: MemoEntity,
    @Relation(parentColumn = "id", entityColumn = "memoId")
    val tagRefs: List<MemoTagRefEntity>,
    @Relation(parentColumn = "id", entityColumn = "memoId")
    val imageRefs: List<MemoImageEntity>
)
