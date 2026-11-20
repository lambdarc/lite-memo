package com.lambdarc.litememo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.util.UUID
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataStoreModule::class])
object TemporaryDataStoreModule {

    @Provides
    @Singleton
    @UserSettingsDataStore
    fun provideUserSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        val file = File(context.cacheDir, "user_settings_${UUID.randomUUID()}.preferences_pb")
        return PreferenceDataStoreFactory.create { file }
    }

}
