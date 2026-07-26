package com.appvoyager.litememo.di

import com.appvoyager.litememo.BuildConfig
import com.appvoyager.litememo.data.export.MemoArchiveLimits
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {

    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    @Singleton
    @AppVersion
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME

    @Provides
    @Singleton
    @ArchiveLimits
    fun provideArchiveLimits(): MemoArchiveLimits = MemoArchiveLimits.DEFAULT

}
