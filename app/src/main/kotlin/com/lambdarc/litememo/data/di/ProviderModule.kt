package com.lambdarc.litememo.data.di

import com.lambdarc.litememo.data.provider.SystemCurrentTimeProvider
import com.lambdarc.litememo.data.provider.UuidMemoIdProvider
import com.lambdarc.litememo.data.provider.UuidMemoImageIdProvider
import com.lambdarc.litememo.data.provider.UuidTagIdProvider
import com.lambdarc.litememo.domain.provider.CurrentTimeProvider
import com.lambdarc.litememo.domain.provider.MemoIdProvider
import com.lambdarc.litememo.domain.provider.MemoImageIdProvider
import com.lambdarc.litememo.domain.provider.TagIdProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    @Provides
    @Singleton
    fun provideCurrentTimeProvider(): CurrentTimeProvider = SystemCurrentTimeProvider()

    @Provides
    @Singleton
    fun provideMemoIdProvider(): MemoIdProvider = UuidMemoIdProvider()

    @Provides
    @Singleton
    fun provideMemoImageIdProvider(): MemoImageIdProvider = UuidMemoImageIdProvider()

    @Provides
    @Singleton
    fun provideTagIdProvider(): TagIdProvider = UuidTagIdProvider()

}
