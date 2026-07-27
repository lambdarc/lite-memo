package com.appvoyager.litememo.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreUserSettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun observeThemeModeReturnsDefaultValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        val result = repository.observeThemeMode().first()

        // Assert
        assertEquals(ThemeMode.SYSTEM, result)
    }

    @Test
    fun observeThemeModeReturnsSavedValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        repository.setThemeMode(ThemeMode.DARK)
        val result = repository.observeThemeMode().first()

        // Assert
        assertEquals(ThemeMode.DARK, result)
    }

    @Test
    fun observeThemeModeReturnsDefaultWhenStoredValueIsInvalid() = runTest {
        // Arrange
        val dataStore = dataStore(backgroundScope)
        dataStore.edit { prefs ->
            prefs[DataStoreUserSettingsRepository.THEME_MODE_KEY] = "NOT_A_VALID_MODE"
        }
        val repository = DataStoreUserSettingsRepository(dataStore)

        // Act
        // Boundary: an unknown stored name falls back to the default instead of throwing.
        val result = repository.observeThemeMode().first()

        // Assert
        assertEquals(ThemeMode.SYSTEM, result)
    }

    @Test
    fun observeMemoSortOrderReturnsDefaultValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        val result = repository.observeMemoSortOrder().first()

        // Assert
        assertEquals(MemoSortOrder.UPDATED_NEWEST, result)
    }

    @Test
    fun observeMemoSortOrderReturnsSavedValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        repository.setMemoSortOrder(MemoSortOrder.CREATED_NEWEST)
        val result = repository.observeMemoSortOrder().first()

        // Assert
        assertEquals(MemoSortOrder.CREATED_NEWEST, result)
    }

    @Test
    fun observeMemoSortOrderReturnsDefaultWhenStoredValueIsInvalid() = runTest {
        // Arrange
        val dataStore = dataStore(backgroundScope)
        dataStore.edit { prefs ->
            prefs[DataStoreUserSettingsRepository.MEMO_SORT_ORDER_KEY] = "NOT_A_VALID_ORDER"
        }
        val repository = DataStoreUserSettingsRepository(dataStore)

        // Act
        // Boundary: an unknown stored name falls back to the default instead of throwing.
        val result = repository.observeMemoSortOrder().first()

        // Assert
        assertEquals(MemoSortOrder.UPDATED_NEWEST, result)
    }

    @Test
    fun observeAppLockEnabledReturnsDefaultValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        val result = repository.observeAppLockEnabled().first()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun observeAppLockEnabledReturnsSavedValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        repository.setAppLockEnabled(true)
        val result = repository.observeAppLockEnabled().first()

        // Assert
        assertEquals(true, result)
    }

    @Test
    fun observeTutorialCompletedReturnsDefaultValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        // Normal: tutorial is incomplete by default
        val result = repository.observeTutorialCompleted().first()

        // Assert
        assertEquals(false, result)
    }

    @Test
    fun observeTutorialCompletedReturnsSavedValue() = runTest {
        // Arrange
        val repository = repository(backgroundScope)

        // Act
        // Normal: completed tutorial flag is saved
        repository.completeTutorial()
        val result = repository.observeTutorialCompleted().first()

        // Assert
        assertEquals(true, result)
    }

    @Test
    fun normalSetThemeModeStoresNameUnderExistingKey() = runTest {
        // Arrange
        val dataStore = dataStore(backgroundScope)
        val repository = DataStoreUserSettingsRepository(dataStore)

        // Act
        // Normal: split display contract keeps writing the persisted theme_mode key
        repository.setThemeMode(ThemeMode.DARK)
        val stored = dataStore.data.first()[stringPreferencesKey("theme_mode")]

        // Assert
        assertEquals(ThemeMode.DARK.name, stored)
    }

    @Test
    fun normalSetMemoSortOrderStoresNameUnderExistingKey() = runTest {
        // Arrange
        val dataStore = dataStore(backgroundScope)
        val repository = DataStoreUserSettingsRepository(dataStore)

        // Act
        // Normal: split display contract keeps writing the persisted memo_sort_order key
        repository.setMemoSortOrder(MemoSortOrder.CREATED_NEWEST)
        val stored = dataStore.data.first()[stringPreferencesKey("memo_sort_order")]

        // Assert
        assertEquals(MemoSortOrder.CREATED_NEWEST.name, stored)
    }

    @Test
    fun normalSetAppLockEnabledStoresFlagUnderExistingKey() = runTest {
        // Arrange
        val dataStore = dataStore(backgroundScope)
        val repository = DataStoreUserSettingsRepository(dataStore)

        // Act
        // Normal: split app lock contract keeps writing the persisted app_lock_enabled key
        repository.setAppLockEnabled(true)
        val stored = dataStore.data.first()[booleanPreferencesKey("app_lock_enabled")]

        // Assert
        assertEquals(true, stored)
    }

    @Test
    fun normalCompleteTutorialStoresFlagUnderExistingKey() = runTest {
        // Arrange
        val dataStore = dataStore(backgroundScope)
        val repository = DataStoreUserSettingsRepository(dataStore)

        // Act
        // Normal: split tutorial contract keeps writing the persisted tutorial_completed key
        repository.completeTutorial()
        val stored = dataStore.data.first()[booleanPreferencesKey("tutorial_completed")]

        // Assert
        assertEquals(true, stored)
    }

    private fun repository(scope: CoroutineScope): DataStoreUserSettingsRepository =
        DataStoreUserSettingsRepository(dataStore(scope))

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(tempDir, "user_settings.preferences_pb")
        }
}
