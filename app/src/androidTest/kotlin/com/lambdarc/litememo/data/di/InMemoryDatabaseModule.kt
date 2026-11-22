package com.lambdarc.litememo.data.di

import android.content.Context
import androidx.room.Room
import com.lambdarc.litememo.data.local.LiteMemoDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object InMemoryDatabaseModule {

    @Provides
    @Singleton
    fun provideLiteMemoDatabase(@ApplicationContext context: Context): LiteMemoDatabase =
        Room.inMemoryDatabaseBuilder(context, LiteMemoDatabase::class.java).build()

}
