package com.lambdarc.litememo.data.di

import com.lambdarc.litememo.data.local.LiteMemoDatabase
import com.lambdarc.litememo.data.local.dao.MemoBulkDao
import com.lambdarc.litememo.data.local.dao.MemoDao
import com.lambdarc.litememo.data.local.dao.TagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun provideMemoDao(database: LiteMemoDatabase): MemoDao = database.memoDao()

    @Provides
    fun provideMemoBulkDao(database: LiteMemoDatabase): MemoBulkDao = database.memoBulkDao()

    @Provides
    fun provideTagDao(database: LiteMemoDatabase): TagDao = database.tagDao()

}
