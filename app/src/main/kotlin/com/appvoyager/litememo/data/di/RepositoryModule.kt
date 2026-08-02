package com.appvoyager.litememo.data.di

import com.appvoyager.litememo.data.repository.DataStoreUserSettingsRepository
import com.appvoyager.litememo.data.repository.FileSystemMemoExportArchiveRepository
import com.appvoyager.litememo.data.repository.FileSystemMemoImageStore
import com.appvoyager.litememo.data.repository.RoomMemoImportRepository
import com.appvoyager.litememo.data.repository.RoomMemoRepository
import com.appvoyager.litememo.data.repository.RoomTagRepository
import com.appvoyager.litememo.data.repository.StagingMemoImportArchiveRepository
import com.appvoyager.litememo.domain.repository.AppLockSettingsRepository
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
import com.appvoyager.litememo.domain.repository.MemoImageStore
import com.appvoyager.litememo.domain.repository.MemoImportArchiveRepository
import com.appvoyager.litememo.domain.repository.MemoImportRepository
import com.appvoyager.litememo.domain.repository.MemoRepository
import com.appvoyager.litememo.domain.repository.TagRepository
import com.appvoyager.litememo.domain.repository.TutorialProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMemoRepository(repository: RoomMemoRepository): MemoRepository

    @Binds
    @Singleton
    abstract fun bindMemoImportRepository(
        repository: RoomMemoImportRepository
    ): MemoImportRepository

    @Binds
    @Singleton
    abstract fun bindMemoImportArchiveRepository(
        repository: StagingMemoImportArchiveRepository
    ): MemoImportArchiveRepository

    @Binds
    @Singleton
    abstract fun bindMemoImageStore(store: FileSystemMemoImageStore): MemoImageStore

    @Binds
    @Singleton
    abstract fun bindTagRepository(repository: RoomTagRepository): TagRepository

    @Binds
    abstract fun bindDisplaySettingsRepository(
        repository: DataStoreUserSettingsRepository
    ): DisplaySettingsRepository

    @Binds
    abstract fun bindAppLockSettingsRepository(
        repository: DataStoreUserSettingsRepository
    ): AppLockSettingsRepository

    @Binds
    abstract fun bindTutorialProgressRepository(
        repository: DataStoreUserSettingsRepository
    ): TutorialProgressRepository

    @Binds
    @Singleton
    abstract fun bindMemoExportArchiveRepository(
        repository: FileSystemMemoExportArchiveRepository
    ): MemoExportArchiveRepository

}
