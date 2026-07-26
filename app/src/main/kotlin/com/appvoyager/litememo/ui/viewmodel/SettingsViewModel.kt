package com.appvoyager.litememo.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appvoyager.litememo.di.AppVersion
import com.appvoyager.litememo.di.ApplicationScope
import com.appvoyager.litememo.domain.exception.ImportTagNameConflictException
import com.appvoyager.litememo.domain.exception.MemoImportException
import com.appvoyager.litememo.domain.exception.MemoImportFailureReason
import com.appvoyager.litememo.domain.model.MemoSortOrder
import com.appvoyager.litememo.domain.model.ThemeMode
import com.appvoyager.litememo.domain.model.value.ExportFileReference
import com.appvoyager.litememo.domain.model.value.MemoExportToken
import com.appvoyager.litememo.domain.repository.MemoExportArchiveRepository
import com.appvoyager.litememo.domain.usecase.ImportMemosFromFileUseCase
import com.appvoyager.litememo.domain.usecase.ObserveAppLockEnabledUseCase
import com.appvoyager.litememo.domain.usecase.ObserveMemoSortOrderUseCase
import com.appvoyager.litememo.domain.usecase.ObserveThemeModeUseCase
import com.appvoyager.litememo.domain.usecase.PrepareMemoExportUseCase
import com.appvoyager.litememo.domain.usecase.SetAppLockEnabledUseCase
import com.appvoyager.litememo.domain.usecase.SetMemoSortOrderUseCase
import com.appvoyager.litememo.domain.usecase.SetThemeModeUseCase
import com.appvoyager.litememo.ui.auth.AppLockAuthenticationUiResult
import com.appvoyager.litememo.ui.state.SettingsImportErrorDialogUiState
import com.appvoyager.litememo.ui.state.SettingsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    observeMemoSortOrderUseCase: ObserveMemoSortOrderUseCase,
    observeAppLockEnabledUseCase: ObserveAppLockEnabledUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val setMemoSortOrderUseCase: SetMemoSortOrderUseCase,
    private val setAppLockEnabledUseCase: SetAppLockEnabledUseCase,
    private val prepareMemoExportUseCase: PrepareMemoExportUseCase,
    private val memoExportArchiveRepository: MemoExportArchiveRepository,
    private val importMemosFromFileUseCase: ImportMemosFromFileUseCase,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    @param:AppVersion private val appVersion: String
) : ViewModel() {

    private val themeDropdownExpanded = MutableStateFlow(false)
    private val sortOrderExpanded = MutableStateFlow(false)
    private val isExporting = MutableStateFlow(false)
    private val exportPickerRequestId = MutableStateFlow<Long?>(null)
    private val isImporting = MutableStateFlow(false)
    private val showImportConfirmDialog = MutableStateFlow(false)
    private val importErrorDialog = MutableStateFlow<SettingsImportErrorDialogUiState?>(null)
    private var pendingImportReference: ExportFileReference? = null
    private var pendingExportToken: MemoExportToken? = null
    private var nextExportPickerRequestId = 0L
    private var isAppLockAuthenticating = false

    private val _snackbarEvent = Channel<SettingsSnackbarUiEvent>(Channel.BUFFERED)
    val snackbarEvent: Flow<SettingsSnackbarUiEvent> = _snackbarEvent.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        observeThemeModeUseCase(),
        observeMemoSortOrderUseCase(),
        observeAppLockEnabledUseCase(),
        combine(
            themeDropdownExpanded,
            sortOrderExpanded,
            isExporting,
            isImporting,
            showImportConfirmDialog
        ) { themeExpanded, expanded, exporting, importing, importDialog ->
            SettingsUiFlags(themeExpanded, expanded, exporting, importing, importDialog)
        }.combine(exportPickerRequestId) { flags, requestId ->
            flags.copy(exportPickerRequestId = requestId)
        },
        importErrorDialog
    ) { themeMode, sortOrder, appLockEnabled, flags, importError ->
        SettingsUiState(
            themeMode = themeMode,
            memoSortOrder = sortOrder,
            appLockEnabled = appLockEnabled,
            appVersion = appVersion,
            themeDropdownExpanded = flags.themeDropdownExpanded,
            sortOrderExpanded = flags.sortOrderExpanded,
            isExporting = flags.isExporting,
            exportPickerRequestId = flags.exportPickerRequestId,
            isImporting = flags.isImporting,
            showImportConfirmDialog = flags.showImportConfirmDialog,
            importErrorDialog = importError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(appVersion = appVersion)
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { runCatching { setThemeModeUseCase(mode) } }
    }

    fun setMemoSortOrder(order: MemoSortOrder) {
        viewModelScope.launch { runCatching { setMemoSortOrderUseCase(order) } }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { runCatching { setAppLockEnabledUseCase(enabled) } }
    }

    // 認証要求が受け付けられたら true。認証中の重複要求は false を返して無視させる。
    fun beginAppLockAuthentication(): Boolean {
        if (isAppLockAuthenticating) return false
        isAppLockAuthenticating = true
        return true
    }

    fun onAppLockEnableAuthenticationResult(result: AppLockAuthenticationUiResult) {
        isAppLockAuthenticating = false
        when (result) {
            AppLockAuthenticationUiResult.SUCCEEDED -> setAppLockEnabled(true)

            AppLockAuthenticationUiResult.NO_DEVICE_CREDENTIAL -> {
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.AppLockNoDeviceCredential)
            }

            AppLockAuthenticationUiResult.UNAVAILABLE -> {
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.AppLockUnavailable)
            }

            AppLockAuthenticationUiResult.FAILED -> {
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.AppLockAuthenticationFailed)
            }

            AppLockAuthenticationUiResult.CANCELED -> {
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.AppLockAuthenticationCanceled)
            }
        }
    }

    fun expandThemeDropdown() {
        sortOrderExpanded.value = false
        themeDropdownExpanded.value = true
    }

    fun collapseThemeDropdown() {
        themeDropdownExpanded.value = false
    }

    fun expandSortOrder() {
        themeDropdownExpanded.value = false
        sortOrderExpanded.value = true
    }

    fun collapseSortOrder() {
        sortOrderExpanded.value = false
    }

    fun prepareExport() {
        if (isExporting.value || isImporting.value) return
        isExporting.value = true
        viewModelScope.launch {
            try {
                pendingExportToken = prepareMemoExportUseCase()
                nextExportPickerRequestId++
                exportPickerRequestId.value = nextExportPickerRequestId
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.ExportError)
                isExporting.value = false
            }
        }
    }

    fun onExportPickerRequestHandled(requestId: Long) {
        if (exportPickerRequestId.value == requestId) {
            exportPickerRequestId.value = null
        }
    }

    fun writePreparedExport(reference: ExportFileReference) {
        val token = pendingExportToken?.takeIf { isExporting.value }
        if (token == null) {
            _snackbarEvent.trySend(SettingsSnackbarUiEvent.ExportError)
            return
        }
        viewModelScope.launch {
            try {
                memoExportArchiveRepository.write(token, reference)
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.ExportSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.ExportDestinationWriteError)
            } finally {
                withContext(NonCancellable) {
                    runCatching { memoExportArchiveRepository.discard(token) }
                }
                clearPreparedExport(token)
            }
        }
    }

    fun onExportPickerHostStopped() {
        if (exportPickerRequestId.value != null) cancelPreparedExport()
    }

    fun cancelPreparedExport() {
        val token = pendingExportToken ?: return
        pendingExportToken = null
        exportPickerRequestId.value = null
        isExporting.value = false
        viewModelScope.launch {
            withContext(NonCancellable) {
                runCatching { memoExportArchiveRepository.discard(token) }
            }
        }
    }

    fun onImportFileSelected(reference: ExportFileReference) {
        if (isExporting.value || isImporting.value) return
        pendingImportReference = reference
        showImportConfirmDialog.value = true
    }

    fun confirmImport() {
        if (isImporting.value || isExporting.value) return
        val reference = pendingImportReference ?: return
        showImportConfirmDialog.value = false
        pendingImportReference = null
        viewModelScope.launch {
            isImporting.value = true
            try {
                importMemosFromFileUseCase(reference)
                _snackbarEvent.trySend(SettingsSnackbarUiEvent.ImportSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportTagNameConflictException) {
                importErrorDialog.value = SettingsImportErrorDialogUiState.TagNameConflict(
                    tagNames = e.tagNames.map { it.value }
                )
            } catch (e: MemoImportException) {
                importErrorDialog.value = e.reason.toImportErrorDialogUiState()
            } catch (_: Throwable) {
                importErrorDialog.value = SettingsImportErrorDialogUiState.Generic
            } finally {
                isImporting.value = false
            }
        }
    }

    fun dismissImportConfirmDialog() {
        showImportConfirmDialog.value = false
        pendingImportReference = null
    }

    fun dismissImportErrorDialog() {
        importErrorDialog.value = null
    }

    override fun onCleared() {
        val token = pendingExportToken
        pendingExportToken = null
        exportPickerRequestId.value = null
        isExporting.value = false
        if (token != null) {
            applicationScope.launch {
                runCatching { memoExportArchiveRepository.discard(token) }
            }
        }
        super.onCleared()
    }

    private fun clearPreparedExport(token: MemoExportToken) {
        if (pendingExportToken == token) {
            pendingExportToken = null
            exportPickerRequestId.value = null
            isExporting.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

sealed interface SettingsSnackbarUiEvent {
    data object ExportSuccess : SettingsSnackbarUiEvent
    data object ExportError : SettingsSnackbarUiEvent
    data object ExportDestinationWriteError : SettingsSnackbarUiEvent
    data object ImportSuccess : SettingsSnackbarUiEvent
    data object AppLockAuthenticationFailed : SettingsSnackbarUiEvent
    data object AppLockAuthenticationCanceled : SettingsSnackbarUiEvent
    data object AppLockNoDeviceCredential : SettingsSnackbarUiEvent
    data object AppLockUnavailable : SettingsSnackbarUiEvent
}

private fun MemoImportFailureReason.toImportErrorDialogUiState(): SettingsImportErrorDialogUiState =
    when (this) {
        MemoImportFailureReason.UNSUPPORTED_VERSION ->
            SettingsImportErrorDialogUiState.UnsupportedVersion

        MemoImportFailureReason.INVALID_ARCHIVE -> SettingsImportErrorDialogUiState.InvalidArchive

        MemoImportFailureReason.INVALID_IMAGE -> SettingsImportErrorDialogUiState.InvalidImage

        MemoImportFailureReason.SIZE_LIMIT_EXCEEDED ->
            SettingsImportErrorDialogUiState.SizeLimitExceeded

        MemoImportFailureReason.INSUFFICIENT_STORAGE ->
            SettingsImportErrorDialogUiState.InsufficientStorage
    }

private data class SettingsUiFlags(
    val themeDropdownExpanded: Boolean,
    val sortOrderExpanded: Boolean,
    val isExporting: Boolean,
    val isImporting: Boolean,
    val showImportConfirmDialog: Boolean,
    val exportPickerRequestId: Long? = null
)
