package com.appvoyager.litememo.ui.route

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.appvoyager.litememo.R
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.ui.auth.AppLockAuthenticationUiResult
import com.appvoyager.litememo.ui.screen.SettingsScreen
import com.appvoyager.litememo.ui.viewmodel.SettingsSnackbarUiEvent
import com.appvoyager.litememo.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val PRIVACY_POLICY_URL = "https://mshi-04.github.io/lite-memo/privacy/"

private val IMPORT_MIME_TYPES = arrayOf("application/zip")

private fun defaultExportFileName(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    return "lite-memo-export-${LocalDateTime.now().format(formatter)}.zip"
}

@Composable
fun SettingsRoute(
    snackbarHostState: SnackbarHostState,
    onRequestAppLockAuthentication: ((AppLockAuthenticationUiResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSourceLicenseClick: () -> Unit = {},
    onTagManageClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) {
            viewModel.cancelPreparedExport()
        } else {
            viewModel.writePreparedExport(uri.toExportFileReference())
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImportFileSelected(it.toExportFileReference()) }
    }

    val exportSuccessMessage = stringResource(R.string.settings_export_success)
    val exportErrorMessage = stringResource(R.string.settings_export_error)
    val exportDestinationWriteErrorMessage =
        stringResource(R.string.settings_export_destination_write_error)
    val importSuccessMessage = stringResource(R.string.settings_import_success)
    val appLockAuthenticationFailedMessage = stringResource(R.string.app_lock_auth_failed)
    val appLockAuthenticationCanceledMessage =
        stringResource(R.string.settings_app_lock_auth_canceled)
    val appLockNoDeviceCredentialMessage =
        stringResource(R.string.settings_app_lock_no_device_credential)
    val appLockUnavailableMessage = stringResource(R.string.settings_app_lock_unavailable)
    val browserNotFoundMessage = stringResource(R.string.settings_browser_not_found)
    val filePickerNotFoundMessage = stringResource(R.string.settings_file_picker_not_found)

    fun launchWithActivityNotFoundSnackbar(launch: () -> Unit, message: String) {
        try {
            launch()
        } catch (_: ActivityNotFoundException) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = true
                )
            }
        }
    }

    val launchFilePicker: (() -> Unit) -> Unit = { launch ->
        launchWithActivityNotFoundSnackbar(launch, filePickerNotFoundMessage)
    }

    val snackbarMessages = SettingsSnackbarMessages(
        exportSuccess = exportSuccessMessage,
        exportError = exportErrorMessage,
        exportDestinationWriteError = exportDestinationWriteErrorMessage,
        importSuccess = importSuccessMessage,
        appLockAuthFailed = appLockAuthenticationFailedMessage,
        appLockAuthCanceled = appLockAuthenticationCanceledMessage,
        appLockNoDeviceCredential = appLockNoDeviceCredentialMessage,
        appLockUnavailable = appLockUnavailableMessage
    )

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvent.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.toMessage(snackbarMessages),
                withDismissAction = true
            )
        }
    }

    LaunchedEffect(uiState.exportPickerRequestId) {
        val requestId = uiState.exportPickerRequestId ?: return@LaunchedEffect
        try {
            exportLauncher.launch(defaultExportFileName())
            viewModel.onExportPickerRequestHandled(requestId)
        } catch (_: ActivityNotFoundException) {
            viewModel.onExportPickerRequestHandled(requestId)
            viewModel.cancelPreparedExport()
            snackbarHostState.showSnackbar(
                message = filePickerNotFoundMessage,
                withDismissAction = true
            )
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.onExportPickerHostStopped() }
    }

    SettingsScreen(
        uiState = uiState,
        onThemeModeSelect = { viewModel.setThemeMode(it) },
        onMemoSortOrderSelect = { viewModel.setMemoSortOrder(it) },
        onAppLockEnabledChange = { enabled ->
            if (enabled) {
                if (viewModel.beginAppLockAuthentication()) {
                    onRequestAppLockAuthentication { result ->
                        viewModel.onAppLockEnableAuthenticationResult(result)
                    }
                }
            } else {
                viewModel.setAppLockEnabled(false)
            }
        },
        onExpandThemeDropdown = { viewModel.expandThemeDropdown() },
        onCollapseThemeDropdown = { viewModel.collapseThemeDropdown() },
        onExpandSortOrder = { viewModel.expandSortOrder() },
        onCollapseSortOrder = { viewModel.collapseSortOrder() },
        onTagManageClick = onTagManageClick,
        onTrashClick = onTrashClick,
        onExportClick = { viewModel.prepareExport() },
        onImportClick = { launchFilePicker { importLauncher.launch(IMPORT_MIME_TYPES) } },
        onConfirmImport = { viewModel.confirmImport() },
        onDismissImportConfirmDialog = { viewModel.dismissImportConfirmDialog() },
        onDismissImportErrorDialog = { viewModel.dismissImportErrorDialog() },
        onPrivacyPolicyClick = {
            launchWithActivityNotFoundSnackbar(
                launch = {
                    val intent = Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
                    context.startActivity(intent)
                },
                message = browserNotFoundMessage
            )
        },
        onOpenSourceLicenseClick = onOpenSourceLicenseClick,
        modifier = modifier
    )
}

private fun Uri.toExportFileReference(): ExportFileReference = ExportFileReference(toString())

private fun SettingsSnackbarUiEvent.toMessage(messages: SettingsSnackbarMessages): String =
    when (this) {
        SettingsSnackbarUiEvent.ExportSuccess -> messages.exportSuccess
        SettingsSnackbarUiEvent.ExportError -> messages.exportError
        SettingsSnackbarUiEvent.ExportDestinationWriteError -> messages.exportDestinationWriteError
        SettingsSnackbarUiEvent.ImportSuccess -> messages.importSuccess
        SettingsSnackbarUiEvent.AppLockAuthenticationFailed -> messages.appLockAuthFailed
        SettingsSnackbarUiEvent.AppLockAuthenticationCanceled -> messages.appLockAuthCanceled
        SettingsSnackbarUiEvent.AppLockNoDeviceCredential -> messages.appLockNoDeviceCredential
        SettingsSnackbarUiEvent.AppLockUnavailable -> messages.appLockUnavailable
    }

private data class SettingsSnackbarMessages(
    val exportSuccess: String,
    val exportError: String,
    val exportDestinationWriteError: String,
    val importSuccess: String,
    val appLockAuthFailed: String,
    val appLockAuthCanceled: String,
    val appLockNoDeviceCredential: String,
    val appLockUnavailable: String
)
