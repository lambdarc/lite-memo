package com.appvoyager.litememo.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appvoyager.litememo.R
import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.ThemeMode
import com.appvoyager.litememo.ui.state.SettingsImportErrorDialogUiState
import com.appvoyager.litememo.ui.state.SettingsUiState
import com.appvoyager.litememo.ui.theme.LiteMemoTheme

private val IMPORT_ERROR_DIALOG_MAX_TEXT_HEIGHT = 240.dp

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeSelect: (ThemeMode) -> Unit,
    onMemoSortOrderSelect: (MemoSortOrder) -> Unit,
    onAppLockEnabledChange: (Boolean) -> Unit,
    onExpandThemeDropdown: () -> Unit,
    onCollapseThemeDropdown: () -> Unit,
    onExpandSortOrder: () -> Unit,
    onCollapseSortOrder: () -> Unit,
    onTagManageClick: () -> Unit,
    onTrashClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onConfirmImport: () -> Unit,
    onDismissImportConfirmDialog: () -> Unit,
    onDismissImportErrorDialog: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onOpenSourceLicenseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 48.dp
            )
        ) {
            item {
                SectionHeader(text = stringResource(R.string.settings_section_display))
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                ThemeRow(
                    currentMode = uiState.themeMode,
                    expanded = uiState.themeDropdownExpanded,
                    onExpand = onExpandThemeDropdown,
                    onCollapse = onCollapseThemeDropdown,
                    onSelect = onThemeModeSelect
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SortOrderRow(
                    currentOrder = uiState.memoSortOrder,
                    expanded = uiState.sortOrderExpanded,
                    onExpand = onExpandSortOrder,
                    onCollapse = onCollapseSortOrder,
                    onSelect = onMemoSortOrderSelect
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SettingsClickableRow(
                    label = stringResource(R.string.settings_tag_manage),
                    onClick = onTagManageClick,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SettingsClickableRow(
                    label = stringResource(R.string.settings_trash),
                    onClick = onTrashClick,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_section_privacy))
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_app_lock),
                    checked = uiState.appLockEnabled,
                    onCheckedChange = onAppLockEnabledChange
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(
                    text = stringResource(R.string.settings_section_data)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                SettingsClickableRow(
                    label = stringResource(R.string.settings_export),
                    onClick = onExportClick,
                    enabled = !uiState.isExporting && !uiState.isImporting,
                    testTag = "settingsExportAction",
                    trailingIcon = {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SettingsClickableRow(
                    label = stringResource(R.string.settings_import),
                    onClick = onImportClick,
                    enabled = !uiState.isExporting && !uiState.isImporting,
                    testTag = "settingsImportAction",
                    trailingIcon = {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("settingsImportLoadingIndicator"),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_section_app_info))
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                VersionRow(version = uiState.appVersion)
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SettingsClickableRow(
                    label = stringResource(R.string.settings_privacy_policy),
                    onClick = onPrivacyPolicyClick,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            item {
                SettingsClickableRow(
                    label = stringResource(R.string.settings_open_source_licenses),
                    onClick = onOpenSourceLicenseClick,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }
    }

    if (uiState.showImportConfirmDialog) {
        ImportConfirmDialog(
            onConfirm = onConfirmImport,
            onDismiss = onDismissImportConfirmDialog
        )
    }

    uiState.importErrorDialog?.let { dialogState ->
        ImportErrorDialog(state = dialogState, onDismiss = onDismissImportErrorDialog)
    }

}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ThemeRow(
    currentMode: ThemeMode,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = if (expanded) onCollapse else onExpand)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentMode.toDisplayString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onCollapse
            ) {
                ThemeMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(text = mode.toDisplayString()) },
                        onClick = {
                            onSelect(mode)
                            onCollapse()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SortOrderRow(
    currentOrder: MemoSortOrder,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSelect: (MemoSortOrder) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = if (expanded) onCollapse else onExpand)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_sort_order),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currentOrder.toDisplayString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = onCollapse
            ) {
                MemoSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(text = order.toDisplayString()) },
                        onClick = {
                            onSelect(order)
                            onCollapse()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VersionRow(version: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = version,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun SettingsClickableRow(
    label: String,
    onClick: () -> Unit,
    trailingIcon: @Composable () -> Unit,
    enabled: Boolean = true,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(modifier = Modifier.weight(1f))
        trailingIcon()
    }
}

@Composable
private fun ImportConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_import_confirm_title))
        },
        text = {
            Text(text = stringResource(R.string.settings_import_confirm_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.settings_import_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel_label))
            }
        }
    )
}

@Composable
private fun ImportErrorDialog(state: SettingsImportErrorDialogUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.settings_import_error_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = IMPORT_ERROR_DIALOG_MAX_TEXT_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .testTag("settingsImportErrorDialogText")
            ) {
                Text(text = state.toMessage())
                if (state is SettingsImportErrorDialogUiState.TagNameConflict) {
                    state.tagNames.forEach { tagName ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = tagName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("settingsImportErrorDialogClose")
            ) {
                Text(text = stringResource(R.string.settings_import_error_close))
            }
        }
    )
}

@Composable
private fun SettingsImportErrorDialogUiState.toMessage(): String = when (this) {
    is SettingsImportErrorDialogUiState.TagNameConflict ->
        stringResource(R.string.settings_import_error_tag_conflict_message)

    SettingsImportErrorDialogUiState.UnsupportedVersion ->
        stringResource(R.string.settings_import_error_unsupported_version)

    SettingsImportErrorDialogUiState.InvalidArchive ->
        stringResource(R.string.settings_import_error_invalid_archive)

    SettingsImportErrorDialogUiState.InvalidImage ->
        stringResource(R.string.settings_import_error_invalid_image)

    SettingsImportErrorDialogUiState.SizeLimitExceeded ->
        stringResource(R.string.settings_import_error_size_limit)

    SettingsImportErrorDialogUiState.InsufficientStorage ->
        stringResource(R.string.settings_import_error_insufficient_storage)

    SettingsImportErrorDialogUiState.Generic -> stringResource(R.string.settings_import_error)
}

@Composable
private fun ThemeMode.toDisplayString(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    LiteMemoTheme {
        SettingsScreen(
            uiState = SettingsUiState(appVersion = "1.0.0"),
            onThemeModeSelect = {},
            onMemoSortOrderSelect = {},
            onAppLockEnabledChange = {},
            onExpandThemeDropdown = {},
            onCollapseThemeDropdown = {},
            onExpandSortOrder = {},
            onCollapseSortOrder = {},
            onTagManageClick = {},
            onTrashClick = {},
            onExportClick = {},
            onImportClick = {},
            onConfirmImport = {},
            onDismissImportConfirmDialog = {},
            onDismissImportErrorDialog = {},
            onPrivacyPolicyClick = {},
            onOpenSourceLicenseClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsRowsPreview() {
    LiteMemoTheme {
        Column(modifier = Modifier.padding(24.dp)) {
            SectionHeader(text = stringResource(R.string.settings_section_display))
            ThemeRow(
                currentMode = ThemeMode.SYSTEM,
                expanded = false,
                onExpand = {},
                onCollapse = {},
                onSelect = {}
            )
            SortOrderRow(
                currentOrder = MemoSortOrder.UPDATED_NEWEST,
                expanded = false,
                onExpand = {},
                onCollapse = {},
                onSelect = {}
            )
            VersionRow(version = "1.0.0")
            SettingsSwitchRow(
                label = stringResource(R.string.settings_app_lock),
                checked = true,
                onCheckedChange = {}
            )
            SettingsClickableRow(
                label = stringResource(R.string.settings_open_source_licenses),
                onClick = {},
                trailingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportConfirmDialogPreview() {
    LiteMemoTheme {
        ImportConfirmDialog(
            onConfirm = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ImportErrorDialogPreview() {
    LiteMemoTheme {
        ImportErrorDialog(
            state = SettingsImportErrorDialogUiState.TagNameConflict(
                tagNames = listOf("仕事", "買い物")
            ),
            onDismiss = {}
        )
    }
}
