package com.lambdarc.litememo.data.di

import android.content.Context
import androidx.room.Room
import com.lambdarc.litememo.data.local.LiteMemoDatabase
import com.lambdarc.litememo.data.local.migration.LiteMemoMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideLiteMemoDatabase(@ApplicationContext context: Context): LiteMemoDatabase =
        Room.databaseBuilder(
            context,
            LiteMemoDatabase::class.java,
            LiteMemoDatabase.DATABASE_NAME
        ).apply {
            LiteMemoMigrations.ALL.forEach { migration -> addMigrations(migration) }
        }.build()

}
