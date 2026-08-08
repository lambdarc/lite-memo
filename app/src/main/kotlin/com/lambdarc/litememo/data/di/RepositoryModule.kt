package com.lambdarc.litememo.data.di

import com.lambdarc.litememo.data.repository.DataStoreUserSettingsRepository
import com.lambdarc.litememo.data.repository.FileSystemMemoExportArchiveRepository
import com.lambdarc.litememo.data.repository.FileSystemMemoImageStore
import com.lambdarc.litememo.data.repository.RoomMemoImportRepository
import com.lambdarc.litememo.data.repository.RoomMemoRepository
import com.lambdarc.litememo.data.repository.RoomTagRepository
import com.lambdarc.litememo.data.repository.StagingMemoImportArchiveRepository
import com.lambdarc.litememo.domain.repository.AppLockSettingsRepository
import com.lambdarc.litememo.domain.repository.DisplaySettingsRepository
import com.lambdarc.litememo.domain.repository.MemoExportArchiveRepository
import com.lambdarc.litememo.domain.repository.MemoImageStore
import com.lambdarc.litememo.domain.repository.MemoImportArchiveRepository
import com.lambdarc.litememo.domain.repository.MemoImportRepository
import com.lambdarc.litememo.domain.repository.MemoRepository
import com.lambdarc.litememo.domain.repository.TagRepository
import com.lambdarc.litememo.domain.repository.TutorialProgressRepository
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
