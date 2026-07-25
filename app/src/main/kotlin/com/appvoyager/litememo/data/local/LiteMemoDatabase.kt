package com.appvoyager.litememo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.appvoyager.litememo.data.local.dao.MemoBulkDao
import com.appvoyager.litememo.data.local.dao.MemoDao
import com.appvoyager.litememo.data.local.dao.TagDao
import com.appvoyager.litememo.data.local.entity.MemoEntity
import com.appvoyager.litememo.data.local.entity.MemoImageEntity
import com.appvoyager.litememo.data.local.entity.MemoTagRefEntity
import com.appvoyager.litememo.data.local.entity.TagEntity

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
