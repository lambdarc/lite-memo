package com.appvoyager.litememo.ui.state

import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.ThemeMode

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val memoSortOrder: MemoSortOrder = MemoSortOrder.UPDATED_NEWEST,
    val appLockEnabled: Boolean = false,
    val appVersion: String = "",
    val themeDropdownExpanded: Boolean = false,
    val sortOrderExpanded: Boolean = false,
    val isExporting: Boolean = false,
    val exportPickerRequestId: Long? = null,
    val isImporting: Boolean = false,
    val showImportConfirmDialog: Boolean = false,
    val importErrorDialog: SettingsImportErrorDialogUiState? = null
)

sealed interface SettingsImportErrorDialogUiState {

    data class TagNameConflict(val tagNames: List<String>) : SettingsImportErrorDialogUiState

    data object UnsupportedVersion : SettingsImportErrorDialogUiState

    data object InvalidArchive : SettingsImportErrorDialogUiState

    data object InvalidImage : SettingsImportErrorDialogUiState

    data object SizeLimitExceeded : SettingsImportErrorDialogUiState

    data object InsufficientStorage : SettingsImportErrorDialogUiState

    data object Generic : SettingsImportErrorDialogUiState
}
