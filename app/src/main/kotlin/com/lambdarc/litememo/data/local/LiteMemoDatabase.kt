package com.lambdarc.litememo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lambdarc.litememo.data.local.dao.MemoBulkDao
import com.lambdarc.litememo.data.local.dao.MemoDao
import com.lambdarc.litememo.data.local.dao.TagDao
import com.lambdarc.litememo.data.local.entity.MemoEntity
import com.lambdarc.litememo.data.local.entity.MemoImageEntity
import com.lambdarc.litememo.data.local.entity.MemoTagRefEntity
import com.lambdarc.litememo.data.local.entity.TagEntity

@Database(
    entities = [
        MemoEntity::class,
        TagEntity::class,
        MemoTagRefEntity::class,
        MemoImageEntity::class
    ],
    version = 1
)
abstract class LiteMemoDatabase : RoomDatabase() {

    abstract fun memoDao(): MemoDao

    abstract fun memoBulkDao(): MemoBulkDao

    abstract fun tagDao(): TagDao

    companion object {
        const val DATABASE_NAME = "lite_memo.db"
    }

}
