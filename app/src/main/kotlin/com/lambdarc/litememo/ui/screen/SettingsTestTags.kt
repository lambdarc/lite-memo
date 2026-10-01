package com.lambdarc.litememo.ui.screen

import com.lambdarc.litememo.domain.model.MemoSortOrder
import com.lambdarc.litememo.domain.model.ThemeMode

object SettingsTestTags {
    const val THEME_ROW = "settingsThemeRow"
    const val THEME_MENU = "settingsThemeMenu"
    const val SORT_ORDER_ROW = "settingsSortOrderRow"
    const val SORT_ORDER_MENU = "settingsSortOrderMenu"
    const val EXPORT_ACTION = "settingsExportAction"
    const val IMPORT_ACTION = "settingsImportAction"
    const val IMPORT_LOADING_INDICATOR = "settingsImportLoadingIndicator"
    const val IMPORT_ERROR_DIALOG_TEXT = "settingsImportErrorDialogText"
    const val IMPORT_ERROR_DIALOG_CLOSE = "settingsImportErrorDialogClose"

    fun themeOption(mode: ThemeMode) = "settingsThemeOption_${mode.name}"

    fun sortOrderOption(order: MemoSortOrder) = "settingsSortOrderOption_${order.name}"
}
