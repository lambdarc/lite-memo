package com.appvoyager.litememo.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appvoyager.litememo.data.util.dataOrEmptyOnIoError
import com.appvoyager.litememo.di.UserSettingsDataStore
import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.ThemeMode
import com.appvoyager.litememo.domain.repository.AppLockSettingsRepository
import com.appvoyager.litememo.domain.repository.DisplaySettingsRepository
import com.appvoyager.litememo.domain.repository.TutorialProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreUserSettingsRepository @Inject constructor(
    @param:UserSettingsDataStore private val dataStore: DataStore<Preferences>
) : DisplaySettingsRepository,
    AppLockSettingsRepository,
    TutorialProgressRepository {

    private val preferencesFlow: Flow<Preferences> = dataStore.dataOrEmptyOnIoError()

    override fun observeThemeMode(): Flow<ThemeMode> = preferencesFlow.map { prefs ->
        val name = prefs[THEME_MODE_KEY]
        name?.let { runCatching { enumValueOf<ThemeMode>(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    override fun observeMemoSortOrder(): Flow<MemoSortOrder> = preferencesFlow.map { prefs ->
        val name = prefs[MEMO_SORT_ORDER_KEY]
        name?.let { runCatching { enumValueOf<MemoSortOrder>(it) }.getOrNull() }
            ?: MemoSortOrder.UPDATED_NEWEST
    }

    override fun observeAppLockEnabled(): Flow<Boolean> = preferencesFlow.map { prefs ->
        prefs[APP_LOCK_ENABLED_KEY] ?: false
    }

    override fun observeTutorialCompleted(): Flow<Boolean> = preferencesFlow.map { prefs ->
        prefs[TUTORIAL_COMPLETED_KEY] ?: false
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name }
    }

    override suspend fun setMemoSortOrder(order: MemoSortOrder) {
        dataStore.edit { prefs -> prefs[MEMO_SORT_ORDER_KEY] = order.name }
    }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[APP_LOCK_ENABLED_KEY] = enabled }
    }

    override suspend fun completeTutorial() {
        dataStore.edit { prefs -> prefs[TUTORIAL_COMPLETED_KEY] = true }
    }

    companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        val MEMO_SORT_ORDER_KEY = stringPreferencesKey("memo_sort_order")
        val APP_LOCK_ENABLED_KEY = booleanPreferencesKey("app_lock_enabled")
        val TUTORIAL_COMPLETED_KEY = booleanPreferencesKey("tutorial_completed")
    }

}
